package com.izylife.ssi.service;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.dto.OnboardingQrResponse;
import com.izylife.ssi.spid.SpidIntegrationService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OnboardingStateService {

    public enum OnboardingStep {
        VP_REQUEST,
        ISSUER_QR,
        SPID_AUTH
    }

    private final AtomicReference<OnboardingStep> currentStep = new AtomicReference<>(OnboardingStep.VP_REQUEST);
    private final AppProperties appProperties;
    private final QrCodeService qrCodeService;
    private final SpidIntegrationService spidIntegrationService;

    public OnboardingStateService(AppProperties appProperties, QrCodeService qrCodeService, SpidIntegrationService spidIntegrationService) {
        this.appProperties = appProperties;
        this.qrCodeService = qrCodeService;
        this.spidIntegrationService = spidIntegrationService;
    }

    public OnboardingQrResponse getCurrentQr() {
        OnboardingStep step = currentStep.get();
        return switch (step) {
            case ISSUER_QR -> buildIssuerQr();
            case SPID_AUTH -> buildSpidAuth();
            case VP_REQUEST -> buildVerifierQr();
        };
    }

    public void showIssuerQr() {
        currentStep.set(OnboardingStep.ISSUER_QR);
    }

    public void showSpidAuth() {
        currentStep.set(OnboardingStep.SPID_AUTH);
    }

    public void showVerifierQr() {
        currentStep.set(OnboardingStep.VP_REQUEST);
    }

    public OnboardingStep getCurrentStep() {
        return currentStep.get();
    }

    private OnboardingQrResponse buildVerifierQr() {
        String payload = resolveVerifierPayload();
        String challenge = resolveVerifierChallenge();
        String helperText = challenge.isBlank() ? "Scan with your SSI wallet to share an Izylife credential." : "Challenge: " + challenge;
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
        String payload = resolveIssuerPayload();
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

    private OnboardingQrResponse buildSpidAuth() {
        AppProperties.SpidProperties spidProperties = Optional.ofNullable(appProperties.getSpid())
                .orElseGet(AppProperties.SpidProperties::new);

        String title = Optional.ofNullable(spidProperties.getTitle())
                .filter(value -> !value.isBlank())
                .orElse("Authenticate with SPID");
        String description = Optional.ofNullable(spidProperties.getDescription())
                .filter(value -> !value.isBlank())
                .orElse("Your wallet does not contain the required Izylife credential. Authenticate with SPID to continue.");
        String helperText = Optional.ofNullable(spidProperties.getHelperText())
                .filter(value -> !value.isBlank())
                .orElse("Click the button below to open the SPID login page.");
        String actionLabel = Optional.ofNullable(spidProperties.getButtonLabel())
                .filter(value -> !value.isBlank())
                .orElse("Continue with SPID");
        String actionUrl = Optional.ofNullable(spidProperties.getLoginUrl())
                .filter(value -> !value.isBlank())
                .orElse("https://spid.izylife.example.org/login");

        OnboardingQrResponse response = new OnboardingQrResponse(
                OnboardingStep.SPID_AUTH.name(),
                title,
                description,
                helperText,
                null,
                null,
                actionLabel,
                actionUrl
        );
        response.setSpidProviders(spidIntegrationService.loadProviders());
        return response;
    }

    private String resolveVerifierPayload() {
        return Optional.ofNullable(appProperties.getVerifier())
                .map(AppProperties.VerifierProperties::getQrPayload)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> {
                    String endpoint = Optional.ofNullable(appProperties.getVerifier())
                            .map(AppProperties.VerifierProperties::getEndpoint)
                            .filter(value -> !value.isBlank())
                            .orElse("https://verifier.izylife.example.org");
                    String challenge = resolveVerifierChallenge();
                    if (challenge.isBlank()) {
                        challenge = "demo-challenge";
                    }
                    return "ssi://vp-request?audience=" + endpoint + "&challenge=" + challenge;
                });
    }

    private String resolveVerifierChallenge() {
        return Optional.ofNullable(appProperties.getVerifier())
                .map(AppProperties.VerifierProperties::getChallenge)
                .filter(value -> !value.isBlank())
                .orElse("");
    }

    private String resolveIssuerPayload() {
        return Optional.ofNullable(appProperties.getIssuer())
                .map(AppProperties.IssuerProperties::getQrPayload)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> {
                    String endpoint = Optional.ofNullable(appProperties.getIssuer())
                            .map(AppProperties.IssuerProperties::getEndpoint)
                            .filter(value -> !value.isBlank())
                            .orElse("https://issuer.izylife.example.org");
                    return "ssi://issuer-offer?issuer=" + endpoint;
                });
    }

    private String resolveIssuerOrganisation() {
        return Optional.ofNullable(appProperties.getIssuer())
                .map(AppProperties.IssuerProperties::getOrganizationName)
                .filter(value -> !value.isBlank())
                .orElse("the Izylife issuer");
    }
}
