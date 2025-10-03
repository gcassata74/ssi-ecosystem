package com.izylife.ssi.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyPresentationRequest {
    @NotBlank
    private String presentationPayload;

    @NotBlank
    private String challenge;

    private String presentationSubmission;

    private String state;

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

    public String getPresentationSubmission() {
        return presentationSubmission;
    }

    public void setPresentationSubmission(String presentationSubmission) {
        this.presentationSubmission = presentationSubmission;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
