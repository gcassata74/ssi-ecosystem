package com.izylife.ssi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OnboardingStatusResponse {

    private String currentStep;
    private String issuerState;
    private OnboardingQrResponse verifier;
    private OnboardingQrResponse issuer;
    private String verifierError;
    private String authorizationCode;
    private String authorizationRedirectUri;
    private String authorizationState;

    public OnboardingStatusResponse() {
    }

    public OnboardingStatusResponse(String currentStep, OnboardingQrResponse verifier, OnboardingQrResponse issuer) {
        this(currentStep, null, verifier, issuer);
    }

    public OnboardingStatusResponse(String currentStep, String issuerState, OnboardingQrResponse verifier, OnboardingQrResponse issuer) {
        this.currentStep = currentStep;
        this.issuerState = issuerState;
        this.verifier = verifier;
        this.issuer = issuer;
        this.verifierError = null;
    }
}
