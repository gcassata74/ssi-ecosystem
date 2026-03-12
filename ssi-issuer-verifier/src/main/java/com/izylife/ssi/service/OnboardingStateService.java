package com.izylife.ssi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.dto.CredentialPreviewDto;
import com.izylife.ssi.dto.OnboardingQrResponse;
import com.izylife.ssi.dto.OnboardingStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OnboardingStateService {

    public enum OnboardingStep {
        VP_REQUEST,
        ISSUER_SPID_PROMPT,
        ISSUER_QR
    }

    public enum IssuerFlowState {
        IDLE,
        WAITING_FOR_WALLET,
        CREDENTIALS_RECEIVED
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingStateService.class);
    private final AtomicReference<OnboardingStep> currentStep = new AtomicReference<>(OnboardingStep.VP_REQUEST);
    private final AtomicReference<IssuerFlowState> issuerFlowState = new AtomicReference<>(IssuerFlowState.IDLE);
    private static final String ONBOARDING_TOPIC = "/topic/onboarding";

    private final AppProperties appProperties;
    private final QrCodeService qrCodeService;
    private final Oidc4VpRequestService oidc4VpRequestService;
    private final Oidc4vciService oidc4vciService;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CredentialOfferContext> activeCredentialOffer = new AtomicReference<>();
    private final AtomicReference<Oidc4VpRequestService.AuthorizationRequest> currentAuthorization = new AtomicReference<>();
    private final AtomicReference<VerifierClientContext> clientContext = new AtomicReference<>(
            new VerifierClientContext(null, null, null)
    );
    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicReference<CredentialPreviewDto> lastVerifiedCredential = new AtomicReference<>();

    public OnboardingStateService(AppProperties appProperties,
                                  QrCodeService qrCodeService,
                                  Oidc4VpRequestService oidc4VpRequestService,
                                  Oidc4vciService oidc4vciService,
                                  ObjectMapper objectMapper,
                                  SimpMessagingTemplate messagingTemplate) {
        this.appProperties = appProperties;
        this.qrCodeService = qrCodeService;
        this.oidc4VpRequestService = oidc4VpRequestService;
        this.oidc4vciService = oidc4vciService;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        refreshAuthorizationRequest(clientContext.get());
    }

    public OnboardingStatusResponse getCurrentStatus() {
        return new OnboardingStatusResponse(
                currentStep.get().name(),
                issuerFlowState.get().name(),
                buildVerifierSnapshot(),
                buildIssuerState()
        );
    }

    public OnboardingQrResponse getCurrentQr() {
        OnboardingStep step = currentStep.get();
        if (step == OnboardingStep.VP_REQUEST) {
            return buildVerifierQr();
        }
        return buildIssuerState();
    }

    public void updateClientContext(String clientId, String redirectUri, String state) {
        VerifierClientContext updated = clientContext.updateAndGet(existing -> {
            String resolvedClientId = isNotBlank(clientId) ? clientId : existing.clientId();
            String resolvedRedirect = isNotBlank(redirectUri) ? redirectUri : existing.redirectUri();
            String resolvedState = isNotBlank(state) ? state : existing.authorizationState();
            if (Objects.equals(resolvedClientId, existing.clientId())
                    && Objects.equals(resolvedRedirect, existing.redirectUri())
                    && Objects.equals(resolvedState, existing.authorizationState())) {
                return existing;
            }
            return new VerifierClientContext(resolvedClientId, resolvedRedirect, resolvedState);
        });

        if (updated != null && isNotBlank(updated.redirectUri())) {
            Oidc4VpRequestService.AuthorizationRequest authorization = currentAuthorization.get();
            if (authorization == null || !Objects.equals(updated.redirectUri(), authorization.redirectUri())) {
                refreshAuthorizationRequest(updated);
            }
        }
    }

    public OnboardingQrResponse getIssuerQr() {
        OnboardingStep step = currentStep.get();
        if (step == OnboardingStep.ISSUER_SPID_PROMPT) {
            return buildSpidPrompt();
        }
        if (step == OnboardingStep.ISSUER_QR) {
            return buildCredentialOfferQr();
        }

        boolean spidEnabled = Optional.ofNullable(appProperties.getSpid())
                .map(AppProperties.SpidProperties::isEnabled)
                .orElse(false);
        if (spidEnabled) {
            return buildSpidPrompt();
        }
        return buildCredentialOfferQr();
    }

    public void publishVerifierError(String message) {
        Oidc4VpRequestService.AuthorizationRequest authorization = ensureActiveAuthorization().authorization();
        OnboardingQrResponse verifier = buildVerifierQr(authorization);
        OnboardingQrResponse issuer = buildIssuerState();
        OnboardingStatusResponse status = new OnboardingStatusResponse(
                currentStep.get().name(),
                issuerFlowState.get().name(),
                verifier,
                issuer
        );
        status.setVerifierError(message);
        messagingTemplate.convertAndSend(ONBOARDING_TOPIC, status);
    }

    public void publishAuthorizationCode(String code, String state, String redirectUri) {
        OnboardingQrResponse verifier = buildVerifierSnapshot();
        OnboardingQrResponse issuer = buildIssuerState();
        OnboardingStatusResponse status = new OnboardingStatusResponse(
                currentStep.get().name(),
                issuerFlowState.get().name(),
                verifier,
                issuer
        );
        status.setAuthorizationCode(code);
        status.setAuthorizationState(state);
        status.setAuthorizationRedirectUri(redirectUri);
        issuerFlowState.set(IssuerFlowState.IDLE);
        messagingTemplate.convertAndSend(ONBOARDING_TOPIC, status);
    }

    public void promptIssuerEnrollment() {
        AppProperties.SpidProperties spidProperties = appProperties.getSpid();
        lastVerifiedCredential.set(null);
        if (spidProperties == null || !spidProperties.isEnabled()) {
            CredentialOfferContext context = activeCredentialOffer.get();
            if (context == null) {
                activeCredentialOffer.compareAndSet(null, createCredentialOfferContext(buildDefaultStaffProfile()));
            }
            showIssuerCredentialOffer();
            return;
        }
        currentStep.set(OnboardingStep.ISSUER_SPID_PROMPT);
        issuerFlowState.set(IssuerFlowState.WAITING_FOR_WALLET);
        activeCredentialOffer.set(null);
        OnboardingQrResponse prompt = buildSpidPrompt();
        publishUpdate(OnboardingStep.ISSUER_SPID_PROMPT, prompt);
    }

    public void showIssuerCredentialOffer() {
        currentStep.set(OnboardingStep.ISSUER_QR);
        issuerFlowState.set(IssuerFlowState.WAITING_FOR_WALLET);
        OnboardingQrResponse issuerQr = buildCredentialOfferQr();
        publishUpdate(OnboardingStep.ISSUER_QR, issuerQr);
    }

    public void showVerifierQr() {
        Oidc4VpRequestService.AuthorizationRequest authorization = refreshAuthorizationRequest(clientContext.get());
        currentStep.set(OnboardingStep.VP_REQUEST);
        OnboardingQrResponse verifierQr = buildVerifierQr(authorization);
        publishUpdate(OnboardingStep.VP_REQUEST, verifierQr);
        issuerFlowState.compareAndSet(IssuerFlowState.CREDENTIALS_RECEIVED, IssuerFlowState.IDLE);
    }

    public void recordVerifiedCredential(CredentialPreviewDto preview) {
        if (preview != null) {
            lastVerifiedCredential.set(preview);
        }
        showVerifierQr();
    }

    public void clearVerifiedCredential() {
        lastVerifiedCredential.set(null);
    }

    public boolean acknowledgeIssuerCredentialsReceived() {
        boolean transitioned = issuerFlowState.compareAndSet(
                IssuerFlowState.WAITING_FOR_WALLET,
                IssuerFlowState.CREDENTIALS_RECEIVED
        );
        if (transitioned) {
            showVerifierQr();
            issuerFlowState.set(IssuerFlowState.IDLE);
        }
        return transitioned;
    }

    public void completeIssuerEnrollmentWithSpid(Oidc4vciService.StaffProfile profile) {
        if (profile == null) {
            return;
        }
        CredentialOfferContext context = createCredentialOfferContext(profile);
        activeCredentialOffer.set(context);
        showIssuerCredentialOffer();
    }

    public OnboardingStep getCurrentStep() {
        return currentStep.get();
    }

    public boolean isActiveAuthorizationState(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        Oidc4VpRequestService.AuthorizationRequest authorization = currentAuthorization.get();
        return authorization != null && state.equals(authorization.state());
    }

    private OnboardingQrResponse buildVerifierQr() {
        AuthorizationState state = ensureActiveAuthorization();
        OnboardingQrResponse response = buildVerifierQr(state.authorization());
        if (state.refreshed()) {
            publishUpdate(OnboardingStep.VP_REQUEST, response);
        }
        return response;
    }

    private OnboardingQrResponse buildVerifierQr(Oidc4VpRequestService.AuthorizationRequest authorization) {
        String payload = authorization.qrPayload();
        String helperText = "State: " + authorization.state() + " | Nonce: " + authorization.nonce();
        return new OnboardingQrResponse(
                OnboardingStep.VP_REQUEST.name(),
                "Verifiable Presentation Request",
                "Scan this code with your SSI wallet to continue the verification flow.",
                helperText,
                payload,
                qrCodeService.generatePngDataUri(payload)
        ).withCredentialPreview(lastVerifiedCredential.get());
    }

    private OnboardingQrResponse buildIssuerState() {
        OnboardingStep step = currentStep.get();
        if (step == OnboardingStep.ISSUER_SPID_PROMPT) {
            return buildSpidPrompt();
        }
        return buildIssuerQr();
    }

    private OnboardingQrResponse buildIssuerQr() {
        return resolveConfiguredIssuerPayload()
                .map(this::buildConfiguredIssuerQr)
                .orElseGet(this::buildCredentialOfferQr);
    }

    private Optional<String> resolveConfiguredIssuerPayload() {
        return Optional.ofNullable(appProperties.getIssuer())
                .map(AppProperties.IssuerProperties::getCredentialOfferUri)
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

    private void publishUpdate(OnboardingStep activeStep, OnboardingQrResponse stepQr) {
        if (stepQr == null) {
            return;
        }

        OnboardingQrResponse verifier = buildVerifierSnapshot();
        OnboardingQrResponse issuer = buildIssuerState();

        if (activeStep == OnboardingStep.VP_REQUEST) {
            verifier = stepQr;
        } else if (activeStep == OnboardingStep.ISSUER_SPID_PROMPT || activeStep == OnboardingStep.ISSUER_QR) {
            issuer = stepQr;
        }

        OnboardingStatusResponse status = new OnboardingStatusResponse(
                activeStep.name(),
                issuerFlowState.get().name(),
                verifier,
                issuer
        );

        LOGGER.debug("Publishing onboarding update: step={} issuerState={} verifierStep={} issuerStep={}",
                status.getCurrentStep(),
                status.getIssuerState(),
                verifier != null ? verifier.getStep() : null,
                issuer != null ? issuer.getStep() : null);

        messagingTemplate.convertAndSend(ONBOARDING_TOPIC, status);
    }

    private OnboardingQrResponse buildVerifierSnapshot() {
        Oidc4VpRequestService.AuthorizationRequest authorization = currentAuthorization.get();
        if (authorization == null) {
            authorization = refreshAuthorizationRequest(clientContext.get());
        }
        return buildVerifierQr(authorization);
    }

    private AuthorizationState ensureActiveAuthorization() {
        VerifierClientContext context = clientContext.get();
        Oidc4VpRequestService.AuthorizationRequest authorization = currentAuthorization.get();
        boolean refreshed = false;
        if (authorization == null || oidc4VpRequestService.resolveSession(authorization.state()).isEmpty()) {
            authorization = refreshAuthorizationRequest(context);
            refreshed = true;
        }
        return new AuthorizationState(authorization, refreshed);
    }

    private Oidc4VpRequestService.AuthorizationRequest refreshAuthorizationRequest(VerifierClientContext context) {
        Oidc4VpRequestService.AuthorizationRequest authorization = oidc4VpRequestService.createAuthorizationRequest(
                context.redirectUri(),
                context.clientId(),
                context.authorizationState()
        );
        currentAuthorization.set(authorization);
        return authorization;
    }

    private OnboardingQrResponse buildCredentialOfferQr() {
        CredentialOfferContext context = ensureCredentialOfferContext();
        String description = "Wallet has no Izylife staff credential. Scan to start an OIDC4VCI credential offer.";
        return new OnboardingQrResponse(
                OnboardingStep.ISSUER_QR.name(),
                "Import Izylife Staff Credential",
                description,
                context.helperText(),
                context.qrPayload(),
                qrCodeService.generatePngDataUri(context.qrPayload())
        );
    }

    private OnboardingQrResponse buildSpidPrompt() {
        AppProperties.SpidProperties spid = Optional.ofNullable(appProperties.getSpid()).orElseGet(AppProperties.SpidProperties::new);
        String description = "Autenticati con SPID per generare l'offerta di credenziali del personale Izylife.";
        String loginUrl = resolveSpidLoginUrl(spid);
        return new OnboardingQrResponse(
                OnboardingStep.ISSUER_SPID_PROMPT.name(),
                "Accesso richiesto",
                description,
                "Avvia l'autenticazione SPID per proseguire.",
                null,
                null,
                "Entra con SPID",
                loginUrl
        );
    }

    private String resolveSpidLoginUrl(AppProperties.SpidProperties spid) {
        String rawPath = Optional.ofNullable(spid.getLoginPath()).filter(path -> !path.isBlank()).orElse("/saml2/authenticate/" + spid.getRegistrationId());
        if (isAbsoluteUrl(rawPath)) {
            return rawPath;
        }

        String baseUrl = Optional.ofNullable(appProperties.getVerifier())
                .map(AppProperties.VerifierProperties::getEndpoint)
                .filter(this::isNotBlank)
                .orElseGet(() -> Optional.ofNullable(appProperties.getIssuer())
                        .map(AppProperties.IssuerProperties::getEndpoint)
                        .filter(this::isNotBlank)
                        .orElse(""));

        if (baseUrl.isBlank()) {
            return rawPath;
        }

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return rawPath.startsWith("/") ? baseUrl + rawPath : baseUrl + "/" + rawPath;
    }

    private boolean isAbsoluteUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private CredentialOfferContext ensureCredentialOfferContext() {
        CredentialOfferContext context = activeCredentialOffer.get();
        if (context != null && oidc4vciService.findOfferById(context.offer().offerId()).isPresent()) {
            return context;
        }
        CredentialOfferContext refreshed = createCredentialOfferContext(buildDefaultStaffProfile());
        activeCredentialOffer.set(refreshed);
        return refreshed;
    }

    private CredentialOfferContext createCredentialOfferContext(Oidc4vciService.StaffProfile profile) {
        String issuerEndpoint = resolveIssuerEndpoint();
        Oidc4vciService.CredentialOfferRecord offer = oidc4vciService.createStaffCredentialOffer(profile);
        try {
            String helperText = String.format("issuer_state=%s | pre-authorized grant available", offer.issuerState());
            String offerJson = objectMapper.writeValueAsString(oidc4vciService.buildCredentialOffer(offer));
            String encoded = URLEncoder.encode(offerJson, StandardCharsets.UTF_8);
            String qrPayload = "openid-credential-offer://?credential_offer=" + encoded;
            return new CredentialOfferContext(offer, profile, helperText, qrPayload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialise credential offer", ex);
        }
    }

    private Oidc4vciService.StaffProfile buildDefaultStaffProfile() {
        return new Oidc4vciService.StaffProfile(
                "did:key:z6MkjsPve3QFtSobhVYqgv48tSxB6v6y7sgbhR8nTBiq7bYd",
                "Rivera",
                "Jamie",
                "Public Authority Operator",
                "IZY-OPS-001",
                "jamie.rivera@izylife.example"
        );
    }

    private String resolveIssuerEndpoint() {
        return Optional.ofNullable(appProperties.getIssuer())
                .map(AppProperties.IssuerProperties::getEndpoint)
                .filter(value -> !value.isBlank())
                .orElse("http://localhost:9090");
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record CredentialOfferContext(Oidc4vciService.CredentialOfferRecord offer,
                                          Oidc4vciService.StaffProfile profile,
                                          String helperText,
                                          String qrPayload) {
    }

    private record VerifierClientContext(String clientId, String redirectUri, String authorizationState) {
    }

    private record AuthorizationState(Oidc4VpRequestService.AuthorizationRequest authorization, boolean refreshed) {
    }
}
