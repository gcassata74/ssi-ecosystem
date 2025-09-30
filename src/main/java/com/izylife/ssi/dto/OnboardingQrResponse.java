package com.izylife.ssi.dto;

import com.izylife.ssi.spid.SpidProvider;

import java.util.List;

public class OnboardingQrResponse {

    private String step;
    private String title;
    private String description;
    private String helperText;
    private String qrCodePayload;
    private String qrCodeImageDataUrl;
    private String actionLabel;
    private String actionUrl;
    private List<SpidProvider> spidProviders;

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

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHelperText() {
        return helperText;
    }

    public void setHelperText(String helperText) {
        this.helperText = helperText;
    }

    public String getQrCodePayload() {
        return qrCodePayload;
    }

    public void setQrCodePayload(String qrCodePayload) {
        this.qrCodePayload = qrCodePayload;
    }

    public String getQrCodeImageDataUrl() {
        return qrCodeImageDataUrl;
    }

    public void setQrCodeImageDataUrl(String qrCodeImageDataUrl) {
        this.qrCodeImageDataUrl = qrCodeImageDataUrl;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public List<SpidProvider> getSpidProviders() {
        return spidProviders;
    }

    public void setSpidProviders(List<SpidProvider> spidProviders) {
        this.spidProviders = spidProviders;
    }
}
