package com.izylife.ssi.service;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.dto.OnboardingQrResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;
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

    public OnboardingStateService(AppProperties appProperties, QrCodeService qrCodeService) {
        this.appProperties = appProperties;
        this.qrCodeService = qrCodeService;
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

    private String resolveVerifierPayload() {
        return Optional.ofNullable(appProperties.getVerifier())
                .map(AppProperties.VerifierProperties::getQrPayload)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> {
                    String endpoint = resolveVerifierEndpoint();
                    String challenge = resolveVerifierChallenge();
                    if (challenge.isBlank()) {
                        challenge = "demo-challenge";
                    }
                    return "ssi://vp-request?audience=" + endpoint + "&challenge=" + challenge;
                });
    }

    private String resolveVerifierEndpoint() {
        return Optional.ofNullable(appProperties.getVerifier())
                .map(AppProperties.VerifierProperties::getEndpoint)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("Verifier endpoint configuration is missing"));
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
                            .orElse("http://issuer.izylife.com:9090");
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
