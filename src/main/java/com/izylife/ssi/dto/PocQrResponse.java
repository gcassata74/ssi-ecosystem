package com.izylife.ssi.dto;

public class PocQrResponse {

    private String label;
    private String instructions;
    private String payload;
    private String qrImageDataUrl;

    public PocQrResponse() {
    }

    public PocQrResponse(String label, String instructions, String payload, String qrImageDataUrl) {
        this.label = label;
        this.instructions = instructions;
        this.payload = payload;
        this.qrImageDataUrl = qrImageDataUrl;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getQrImageDataUrl() {
        return qrImageDataUrl;
    }

    public void setQrImageDataUrl(String qrImageDataUrl) {
        this.qrImageDataUrl = qrImageDataUrl;
    }
}
