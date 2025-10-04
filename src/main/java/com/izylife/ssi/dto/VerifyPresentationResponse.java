package com.izylife.ssi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyPresentationResponse {
    private boolean valid;
    private String holderDid;
    private String reason;
    private boolean walletHasNoCredential;

    public VerifyPresentationResponse() {
    }

    public VerifyPresentationResponse(boolean valid, String holderDid, String reason) {
        this.valid = valid;
        this.holderDid = holderDid;
        this.reason = reason;
        this.walletHasNoCredential = false;
    }
}
