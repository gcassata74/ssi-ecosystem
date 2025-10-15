package com.izylife.ssi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OnboardingQrResponse {

    private String step;
    private String title;
    private String description;
    private String helperText;
    private String qrCodePayload;
    private String qrCodeImageDataUrl;
    private String actionLabel;
    private String actionUrl;
    private CredentialPreviewDto credentialPreview;

    public OnboardingQrResponse() {
    }

    public OnboardingQrResponse(String step, String title, String description, String helperText, String qrCodePayload, String qrCodeImageDataUrl) {
        this(step, title, description, helperText, qrCodePayload, qrCodeImageDataUrl, null, null);
    }

    public OnboardingQrResponse(String step, String title, String description, String helperText, String qrCodePayload, String qrCodeImageDataUrl, String actionLabel, String actionUrl) {
        this.step = step;
        this.title = title;
        this.description = description;
        this.helperText = helperText;
        this.qrCodePayload = qrCodePayload;
        this.qrCodeImageDataUrl = qrCodeImageDataUrl;
        this.actionLabel = actionLabel;
        this.actionUrl = actionUrl;
    }

    public OnboardingQrResponse withCredentialPreview(CredentialPreviewDto preview) {
        this.credentialPreview = preview;
        return this;
    }
}
