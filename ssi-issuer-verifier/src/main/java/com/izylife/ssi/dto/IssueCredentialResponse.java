package com.izylife.ssi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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
}
