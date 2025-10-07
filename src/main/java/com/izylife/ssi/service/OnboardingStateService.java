package com.izylife.ssi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.dto.OnboardingQrResponse;
import com.izylife.ssi.dto.OnboardingStatusResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OnboardingStateService {

    public enum OnboardingStep {
        VP_REQUEST,
        ISSUER_QR
    }

    public enum IssuerFlowState {
        IDLE,
        WAITING_FOR_WALLET,
        CREDENTIALS_RECEIVED
    }

    private final AtomicReference<OnboardingStep> currentStep = new AtomicReference<>(OnboardingStep.VP_REQUEST);
    private final AtomicReference<IssuerFlowState> issuerFlowState = new AtomicReference<>(IssuerFlowState.IDLE);
    private static final String ONBOARDING_TOPIC = "/topic/onboarding";

    private final AppProperties appProperties;
    private final QrCodeService qrCodeService;
    private final Oidc4VpRequestService oidc4VpRequestService;
    private final Oidc4vciService oidc4vciService;
    private final ObjectMapper objectMapper;
    private final AtomicReference<SampleCredentialOffer> sampleCredentialOffer = new AtomicReference<>();
    private final AtomicReference<Oidc4VpRequestService.AuthorizationRequest> currentAuthorization = new AtomicReference<>();
    private final SimpMessagingTemplate messagingTemplate;

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
        this.sampleCredentialOffer.set(buildSampleCredentialOffer());
        refreshAuthorizationRequest();
    }

    public OnboardingStatusResponse getCurrentStatus() {
        return new OnboardingStatusResponse(
                currentStep.get().name(),
                issuerFlowState.get().name(),
                buildVerifierSnapshot(),
                buildIssuerQr()
        );
    }

    public OnboardingQrResponse getCurrentQr() {
        return currentStep.get() == OnboardingStep.ISSUER_QR
                ? buildIssuerQr()
                : buildVerifierQr();
    }

    public void showIssuerQr() {
        currentStep.set(OnboardingStep.ISSUER_QR);
        issuerFlowState.set(IssuerFlowState.WAITING_FOR_WALLET);
        OnboardingQrResponse issuerQr = buildIssuerQr();
        publishUpdate(OnboardingStep.ISSUER_QR, issuerQr);
    }

    public void showVerifierQr() {
        Oidc4VpRequestService.AuthorizationRequest authorization = refreshAuthorizationRequest();
        currentStep.set(OnboardingStep.VP_REQUEST);
        OnboardingQrResponse verifierQr = buildVerifierQr(authorization);
        publishUpdate(OnboardingStep.VP_REQUEST, verifierQr);
        issuerFlowState.compareAndSet(IssuerFlowState.CREDENTIALS_RECEIVED, IssuerFlowState.IDLE);
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
        );
    }

    private OnboardingQrResponse buildIssuerQr() {
        return resolveConfiguredIssuerPayload()
                .map(this::buildConfiguredIssuerQr)
                .orElseGet(this::buildSampleCredentialQr);
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

    private OnboardingQrResponse buildSampleCredentialQr() {
        SampleCredentialOffer offer = ensureSampleCredentialOffer();
        String payload = offer.qrPayload();
        String description = "Wallet has no Izylife staff credential. Scan to start an OIDC4VCI credential offer.";
        return new OnboardingQrResponse(
                OnboardingStep.ISSUER_QR.name(),
                "Import Sample Staff Credential",
                description,
                offer.helperText(),
                payload,
                qrCodeService.generatePngDataUri(payload)
        );
    }

    private void publishUpdate(OnboardingStep activeStep, OnboardingQrResponse stepQr) {
        if (stepQr == null) {
            return;
        }

        OnboardingQrResponse verifier = activeStep == OnboardingStep.VP_REQUEST
                ? stepQr
                : buildVerifierSnapshot();
        OnboardingQrResponse issuer = activeStep == OnboardingStep.ISSUER_QR
                ? stepQr
                : buildIssuerQr();

        OnboardingStatusResponse status = new OnboardingStatusResponse(
                activeStep.name(),
                issuerFlowState.get().name(),
                verifier,
                issuer
        );

        messagingTemplate.convertAndSend(ONBOARDING_TOPIC, status);
    }

    private OnboardingQrResponse buildVerifierSnapshot() {
        Oidc4VpRequestService.AuthorizationRequest authorization = currentAuthorization.get();
        if (authorization == null) {
            authorization = refreshAuthorizationRequest();
        }
        return buildVerifierQr(authorization);
    }

    private AuthorizationState ensureActiveAuthorization() {
        Oidc4VpRequestService.AuthorizationRequest authorization = currentAuthorization.get();
        boolean refreshed = false;
        if (authorization == null || oidc4VpRequestService.resolveSession(authorization.state()).isEmpty()) {
            authorization = refreshAuthorizationRequest();
            currentStep.set(OnboardingStep.VP_REQUEST);
            refreshed = true;
        }
        return new AuthorizationState(authorization, refreshed);
    }

    private Oidc4VpRequestService.AuthorizationRequest refreshAuthorizationRequest() {
        Oidc4VpRequestService.AuthorizationRequest authorization = oidc4VpRequestService.createAuthorizationRequest();
        currentAuthorization.set(authorization);
        return authorization;
    }

    private SampleCredentialOffer buildSampleCredentialOffer() {
        String issuerEndpoint = resolveIssuerEndpoint();

        Oidc4vciService.StaffProfile profile = new Oidc4vciService.StaffProfile(
                "did:key:z6MkjsPve3QFtSobhVYqgv48tSxB6v6y7sgbhR8nTBiq7bYd",
                "Rivera",
                "Jamie",
                "Public Authority Operator",
                "IZY-OPS-001",
                "jamie.rivera@izylife.example"
        );

        Oidc4vciService.CredentialOfferRecord offer = oidc4vciService.createStaffCredentialOffer(profile);
        try {
            String offerUri = issuerEndpoint + "/oidc4vci/credential-offers/" + offer.offerId();
            String helperText = String.format("issuer_state=%s | pre-authorized grant available", offer.issuerState());
            String offerJson = objectMapper.writeValueAsString(oidc4vciService.buildCredentialOffer(offer));
            String encoded = URLEncoder.encode(offerJson, StandardCharsets.UTF_8);
            String qrPayload = "openid-credential-offer://?credential_offer=" + encoded;
            return new SampleCredentialOffer(
                    offer.offerId(),
                    qrPayload,
                    helperText
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialise sample credential offer", ex);
        }
    }

    private SampleCredentialOffer ensureSampleCredentialOffer() {
        SampleCredentialOffer current = sampleCredentialOffer.get();
        if (current != null && oidc4vciService.findOfferById(current.offerId()).isPresent()) {
            return current;
        }
        SampleCredentialOffer refreshed = buildSampleCredentialOffer();
        sampleCredentialOffer.set(refreshed);
        return refreshed;
    }

    private String resolveIssuerEndpoint() {
        return Optional.ofNullable(appProperties.getIssuer())
                .map(AppProperties.IssuerProperties::getEndpoint)
                .filter(value -> !value.isBlank())
                .orElse("http://localhost:9090");
    }

    private record SampleCredentialOffer(String offerId, String qrPayload, String helperText) {
    }

    private record AuthorizationState(Oidc4VpRequestService.AuthorizationRequest authorization, boolean refreshed) {
    }
}
