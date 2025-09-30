package com.izylife.ssi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private IssuerProperties issuer = new IssuerProperties();
    private VerifierProperties verifier = new VerifierProperties();
    private SpidProperties spid = new SpidProperties();

    public IssuerProperties getIssuer() {
        return issuer;
    }

    public void setIssuer(IssuerProperties issuer) {
        this.issuer = issuer;
    }

    public VerifierProperties getVerifier() {
        return verifier;
    }

    public void setVerifier(VerifierProperties verifier) {
        this.verifier = verifier;
    }

    public SpidProperties getSpid() {
        return spid;
    }

    public void setSpid(SpidProperties spid) {
        this.spid = spid;
    }

    public static class IssuerProperties {
        private String organizationName;
        private String endpoint;
        private String qrPayload;

        public String getOrganizationName() {
            return organizationName;
        }

        public void setOrganizationName(String organizationName) {
            this.organizationName = organizationName;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getQrPayload() {
            return qrPayload;
        }

        public void setQrPayload(String qrPayload) {
            this.qrPayload = qrPayload;
        }
    }

    public static class VerifierProperties {
        private String endpoint;
        private String qrPayload;
        private String challenge;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getQrPayload() {
            return qrPayload;
        }

        public void setQrPayload(String qrPayload) {
            this.qrPayload = qrPayload;
        }

        public String getChallenge() {
            return challenge;
        }

        public void setChallenge(String challenge) {
            this.challenge = challenge;
        }
    }

    public static class SpidProperties {
        private String title;
        private String description;
        private String helperText;
        private String buttonLabel;
        private String loginUrl;

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

        public String getButtonLabel() {
            return buttonLabel;
        }

        public void setButtonLabel(String buttonLabel) {
            this.buttonLabel = buttonLabel;
        }

        public String getLoginUrl() {
            return loginUrl;
        }

        public void setLoginUrl(String loginUrl) {
            this.loginUrl = loginUrl;
        }
    }
}
