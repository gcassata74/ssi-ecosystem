package com.izylife.ssi.dto;

public class IssueCredentialResponse {
    private String credentialId;
    private String encodedCredential;
    private String qrCodePayload;

    public IssueCredentialResponse() {
    }

    public IssueCredentialResponse(String credentialId, String encodedCredential, String qrCodePayload) {
        this.credentialId = credentialId;
        this.encodedCredential = encodedCredential;
        this.qrCodePayload = qrCodePayload;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getEncodedCredential() {
        return encodedCredential;
    }

    public void setEncodedCredential(String encodedCredential) {
        this.encodedCredential = encodedCredential;
    }

    public String getQrCodePayload() {
        return qrCodePayload;
    }

    public void setQrCodePayload(String qrCodePayload) {
        this.qrCodePayload = qrCodePayload;
    }
}
