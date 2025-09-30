package com.izylife.ssi.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyPresentationRequest {
    @NotBlank
    private String presentationPayload;

    @NotBlank
    private String challenge;

    public String getPresentationPayload() {
        return presentationPayload;
    }

    public void setPresentationPayload(String presentationPayload) {
        this.presentationPayload = presentationPayload;
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }
}
