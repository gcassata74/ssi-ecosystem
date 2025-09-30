package com.izylife.ssi.dto;

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

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getHolderDid() {
        return holderDid;
    }

    public void setHolderDid(String holderDid) {
        this.holderDid = holderDid;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isWalletHasNoCredential() {
        return walletHasNoCredential;
    }

    public void setWalletHasNoCredential(boolean walletHasNoCredential) {
        this.walletHasNoCredential = walletHasNoCredential;
    }
}
