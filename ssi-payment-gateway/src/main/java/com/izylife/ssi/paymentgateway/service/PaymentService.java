package com.izylife.ssi.paymentgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.paymentgateway.config.GatewayProperties;
import com.izylife.ssi.paymentgateway.config.IssuerClientProperties;
import com.izylife.ssi.paymentgateway.model.PaymentStatus;
import com.izylife.ssi.paymentgateway.model.PaymentTransaction;
import com.izylife.ssi.paymentgateway.repository.PaymentRepository;
import com.izylife.ssi.paymentgateway.sandbox.SandboxPaymentClient;
import com.izylife.ssi.paymentgateway.sandbox.SandboxPaymentResult;
import com.izylife.ssi.paymentgateway.web.dto.CreatePaymentRequest;
import com.izylife.ssi.paymentgateway.web.dto.PaymentView;
import com.izylife.ssi.paymentgateway.web.dto.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.math.RoundingMode;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repository;
    private final IssuerClientProperties issuerProperties;
    private final GatewayProperties gatewayProperties;
    private final PkceService pkceService;
    private final OidcClient oidcClient;
    private final CredentialPreviewParser credentialPreviewParser;
    private final SandboxPaymentClient sandboxPaymentClient;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentService(PaymentRepository repository,
                          IssuerClientProperties issuerProperties,
                          GatewayProperties gatewayProperties,
                          PkceService pkceService,
                          OidcClient oidcClient,
                          CredentialPreviewParser credentialPreviewParser,
                          SandboxPaymentClient sandboxPaymentClient,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.issuerProperties = issuerProperties;
        this.gatewayProperties = gatewayProperties;
        this.pkceService = pkceService;
        this.oidcClient = oidcClient;
        this.credentialPreviewParser = credentialPreviewParser;
        this.sandboxPaymentClient = sandboxPaymentClient;
        this.objectMapper = objectMapper;
    }

    public PaymentView initiatePayment(CreatePaymentRequest request) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setAmount(normalizeAmount(request.amount()));
        tx.setCurrency(request.currency().toUpperCase());
        tx.setDescription(request.description());
        tx.setReturnUrl(resolveReturnUrl(request.returnUrl()).toString());
        tx.setStatus(PaymentStatus.CREATED);
        tx.setCreatedAt(Instant.now());
        tx.setUpdatedAt(Instant.now());
        tx.setState(randomState());
        tx.setNonce(randomState());
        PkcePair pkcePair = pkceService.createPair();
        tx.setCodeVerifier(pkcePair.codeVerifier());
        tx.setCodeChallenge(pkcePair.codeChallenge());
        tx.setCodeChallengeMethod(pkcePair.method());
        tx.setAuthorizationUrl(buildAuthorizationUrl(tx));
        tx.setStatus(PaymentStatus.AUTHORIZATION_REQUIRED);

        repository.save(tx);
        return PaymentView.from(tx);
    }

    public PaymentView findPayment(String id) {
        PaymentTransaction tx = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment not found"));
        return PaymentView.from(tx);
    }

    public PaymentCallbackResult handleOidcCallback(String state, String code) {
        PaymentTransaction tx = repository.findByState(state)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown state"));

        try {
            TokenResponse tokenResponse = oidcClient.exchangeAuthorizationCode(code, tx.getCodeVerifier());
            CredentialPaymentData paymentData = credentialPreviewParser.toPaymentData(tokenResponse.credentialPreview());
            JsonNode previewNode = objectMapper.valueToTree(tokenResponse.credentialPreview());
            tx.setCredentialPreview(previewNode);
            tx.setHolderDid(paymentData.holderDid());
            tx.setHolderName(paymentData.holderName());
            tx.setPaymentMethodId(paymentData.paymentMethodId());
            tx.setStatus(PaymentStatus.CREDENTIAL_VERIFIED);
            repository.save(tx);

            SandboxPaymentResult sandboxResult = sandboxPaymentClient.authorize(tx, paymentData);
            if (sandboxResult.success()) {
                tx.setSandboxPaymentId(sandboxResult.paymentId());
                tx.setStatus(PaymentStatus.SANDBOX_CONFIRMED);
            } else {
                tx.setFailureReason(sandboxResult.message());
                tx.setStatus(PaymentStatus.FAILED);
            }
            repository.save(tx);
        } catch (Exception ex) {
            log.error("OIDC callback processing failed for payment {}", tx.getId(), ex);
            tx.setFailureReason(ex.getMessage());
            tx.setStatus(PaymentStatus.FAILED);
            repository.save(tx);
        }

        URI redirect = buildReturnRedirect(tx);
        return new PaymentCallbackResult(tx, redirect);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private URI resolveReturnUrl(String requested) {
        if (StringUtils.hasText(requested)) {
            return URI.create(requested);
        }
        return gatewayProperties.getDefaultReturnUrl();
    }

    private String buildAuthorizationUrl(PaymentTransaction tx) {
        return UriComponentsBuilder.fromUri(issuerProperties.resolveAuthorizationEndpoint())
                .queryParam("client_id", issuerProperties.getClientId())
                .queryParam("response_type", "code")
                .queryParam("scope", issuerProperties.getScope())
                .queryParam("redirect_uri", issuerProperties.getRedirectUri())
                .queryParam("code_challenge", tx.getCodeChallenge())
                .queryParam("code_challenge_method", tx.getCodeChallengeMethod())
                .queryParam("state", tx.getState())
                .queryParam("nonce", tx.getNonce())
                .build(true)
                .toUriString();
    }

    private String randomState() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private URI buildReturnRedirect(PaymentTransaction tx) {
        URI returnUrl = Optional.ofNullable(tx.getReturnUrl())
                .map(URI::create)
                .orElse(null);
        if (returnUrl == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(returnUrl)
                .queryParam("paymentId", tx.getId())
                .queryParam("status", tx.getStatus())
                .queryParam("sandboxPaymentId", tx.getSandboxPaymentId())
                .queryParam("failureReason", tx.getFailureReason())
                .build(true)
                .toUri();
    }
}
