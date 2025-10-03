package com.izylife.ssi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Oidc4VpSubmission {

    @JsonProperty("vp_token")
    private String vpToken;

    @JsonProperty("presentation_payload")
    private String presentationPayload;

    @JsonProperty("presentation_submission")
    private String presentationSubmission;

    @JsonProperty("state")
    private String state;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("nonce")
    private String nonce;

    public String getVpToken() {
        return vpToken;
    }

    public void setVpToken(String vpToken) {
        this.vpToken = vpToken;
    }

    public String getPresentationPayload() {
        return presentationPayload;
    }

    public void setPresentationPayload(String presentationPayload) {
        this.presentationPayload = presentationPayload;
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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }
}
