package com.izylife.ssi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.dto.OnboardingQrResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OnboardingStateService {

    public enum OnboardingStep {
        VP_REQUEST,
        ISSUER_QR
    }

    private final AtomicReference<OnboardingStep> currentStep = new AtomicReference<>(OnboardingStep.VP_REQUEST);
    private final AppProperties appProperties;
    private final QrCodeService qrCodeService;
    private final Oidc4VpRequestService oidc4VpRequestService;
    private final ObjectMapper objectMapper;
    private final SampleCredentialOffer sampleCredentialOffer;
    private final AtomicReference<Oidc4VpRequestService.AuthorizationRequest> currentAuthorization = new AtomicReference<>();

    public OnboardingStateService(AppProperties appProperties,
                                  QrCodeService qrCodeService,
                                  Oidc4VpRequestService oidc4VpRequestService,
                                  ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.qrCodeService = qrCodeService;
        this.oidc4VpRequestService = oidc4VpRequestService;
        this.objectMapper = objectMapper;
        this.sampleCredentialOffer = buildSampleCredentialOffer();
        refreshAuthorizationRequest();
    }

    public OnboardingQrResponse getCurrentQr() {
        OnboardingStep step = currentStep.get();
        return switch (step) {
            case ISSUER_QR -> buildIssuerQr();
            case VP_REQUEST -> buildVerifierQr();
        };
    }

    public void showIssuerQr() {
        currentStep.set(OnboardingStep.ISSUER_QR);
    }

    public void showVerifierQr() {
        refreshAuthorizationRequest();
        currentStep.set(OnboardingStep.VP_REQUEST);
    }

    public OnboardingStep getCurrentStep() {
        return currentStep.get();
    }

    private OnboardingQrResponse buildVerifierQr() {
        Oidc4VpRequestService.AuthorizationRequest authorization = ensureActiveAuthorization();
        String payload = authorization.qrPayload();
        String helperText = "State: " + authorization.state() + " | Nonce: " + authorization.nonce();
        return new OnboardingQrResponse(
                OnboardingStep.VP_REQUEST.name(),
                "Verifiable Presentation Request",
                "Scan this code with your SSI wallet to continue the verification flow.",
                helperText,
                payload,
                qrCodeService.generatePngDataUri(payload)
        );
    }

    private OnboardingQrResponse buildIssuerQr() {
        return resolveConfiguredIssuerPayload()
                .map(this::buildConfiguredIssuerQr)
                .orElseGet(this::buildSampleCredentialQr);
    }

    private Optional<String> resolveConfiguredIssuerPayload() {
        return Optional.ofNullable(appProperties.getIssuer())
                .map(AppProperties.IssuerProperties::getQrPayload)
                .filter(value -> !value.isBlank());
    }

    private String resolveIssuerOrganisation() {
        return Optional.ofNullable(appProperties.getIssuer())
                .map(AppProperties.IssuerProperties::getOrganizationName)
                .filter(value -> !value.isBlank())
                .orElse("the Izylife issuer");
    }

    private OnboardingQrResponse buildConfiguredIssuerQr(String payload) {
        String organisation = resolveIssuerOrganisation();
        String description = String.format("No credential found in the wallet. Issue one from %s.", organisation);
        return new OnboardingQrResponse(
                OnboardingStep.ISSUER_QR.name(),
                "Get an Izylife Credential",
                description,
                "Scan to open the issuer onboarding experience.",
                payload,
                qrCodeService.generatePngDataUri(payload)
        );
    }

    private OnboardingQrResponse buildSampleCredentialQr() {
        String payload = sampleCredentialOffer.qrPayload();
        String description = "Wallet has no Izylife staff credential. Scan to import a PoC credential that satisfies the verification request.";
        return new OnboardingQrResponse(
                OnboardingStep.ISSUER_QR.name(),
                "Import Sample Staff Credential",
                description,
                sampleCredentialOffer.helperText(),
                payload,
                qrCodeService.generatePngDataUri(payload),
                "Download credential JSON",
                sampleCredentialOffer.downloadUrl()
        );
    }

    private Oidc4VpRequestService.AuthorizationRequest ensureActiveAuthorization() {
        Oidc4VpRequestService.AuthorizationRequest authorization = currentAuthorization.get();
        if (authorization == null || oidc4VpRequestService.resolveSession(authorization.state()).isEmpty()) {
            authorization = refreshAuthorizationRequest();
        }
        return authorization;
    }

    private Oidc4VpRequestService.AuthorizationRequest refreshAuthorizationRequest() {
        Oidc4VpRequestService.AuthorizationRequest authorization = oidc4VpRequestService.createAuthorizationRequest();
        currentAuthorization.set(authorization);
        return authorization;
    }

    private SampleCredentialOffer buildSampleCredentialOffer() {
        try {
            String organisation = resolveIssuerOrganisation();
            String issuerId = Optional.ofNullable(appProperties.getIssuer())
                    .map(AppProperties.IssuerProperties::getEndpoint)
                    .filter(value -> !value.isBlank())
                    .orElse("did:example:izylife-issuer");

            Map<String, Object> issuer = new LinkedHashMap<>();
            issuer.put("id", issuerId);
            issuer.put("name", organisation);

            Map<String, Object> credential = new LinkedHashMap<>();
            credential.put("@context", List.of(
                    "https://www.w3.org/2018/credentials/v1",
                    "https://www.w3.org/2018/credentials/examples/v1"
            ));
            credential.put("id", "urn:uuid:" + UUID.randomUUID());
            credential.put("type", List.of("VerifiableCredential", "PublicAuthorityStaffCredential"));
            credential.put("issuer", issuer);
            credential.put("issuanceDate", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
            credential.put("credentialSchema", Map.of(
                    "id", "https://schemas.izylife.example/credentials/staff-credential",
                    "type", "JsonSchemaValidator2018"
            ));
            credential.put("credentialSubject", Map.of(
                    "id", "did:example:izylife-operator-001",
                    "givenName", "Jamie",
                    "familyName", "Rivera",
                    "employeeNumber", "IZY-OPS-001",
                    "role", "Public Authority Operator"
            ));

            byte[] credentialBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(credential);
            String qrPayload = "SSICredential:" + Base64.getUrlEncoder().withoutPadding().encodeToString(credentialBytes);
            String downloadUrl = "data:application/json;base64," + Base64.getEncoder().encodeToString(credentialBytes);
            String helperText = "Credential type: PublicAuthorityStaffCredential | Issuer: " + organisation;
            return new SampleCredentialOffer(qrPayload, helperText, downloadUrl);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to prepare the sample Staff Credential QR payload", ex);
        }
    }

    private record SampleCredentialOffer(String qrPayload, String helperText, String downloadUrl) {
    }
}
