package com.izylife.ssi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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
}
