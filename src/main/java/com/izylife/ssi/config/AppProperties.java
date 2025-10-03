package com.izylife.ssi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private IssuerProperties issuer = new IssuerProperties();
    private VerifierProperties verifier = new VerifierProperties();
    private CorsProperties cors = new CorsProperties();

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

    public CorsProperties getCors() {
        return cors;
    }

    public void setCors(CorsProperties cors) {
        this.cors = cors;
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
        private String clientId;
        private String clientIdScheme = "redirect_uri";
        private String responseMode = "direct_post";
        private String requestAudience = "https://self-issued.me/v2";
        private String presentationDefinitionId = "staff-credential";
        private SigningKeyProperties signingKey = new SigningKeyProperties();

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

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientIdScheme() {
            return clientIdScheme;
        }

        public void setClientIdScheme(String clientIdScheme) {
            this.clientIdScheme = clientIdScheme;
        }

        public String getResponseMode() {
            return responseMode;
        }

        public void setResponseMode(String responseMode) {
            this.responseMode = responseMode;
        }

        public String getRequestAudience() {
            return requestAudience;
        }

        public void setRequestAudience(String requestAudience) {
            this.requestAudience = requestAudience;
        }

        public String getPresentationDefinitionId() {
            return presentationDefinitionId;
        }

        public void setPresentationDefinitionId(String presentationDefinitionId) {
            this.presentationDefinitionId = presentationDefinitionId;
        }

        public SigningKeyProperties getSigningKey() {
            return signingKey;
        }

        public void setSigningKey(SigningKeyProperties signingKey) {
            this.signingKey = signingKey;
        }
    }

    public static class CorsProperties {
        private List<String> allowedOrigins = List.of("*");
        private boolean allowCredentials;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }

    public static class SigningKeyProperties {
        private String kid;
        private String kty;
        private String crv;
        private String x;
        private String y;
        private String d;
        private String alg = "ES256";

        public String getKid() {
            return kid;
        }

        public void setKid(String kid) {
            this.kid = kid;
        }

        public String getKty() {
            return kty;
        }

        public void setKty(String kty) {
            this.kty = kty;
        }

        public String getCrv() {
            return crv;
        }

        public void setCrv(String crv) {
            this.crv = crv;
        }

        public String getX() {
            return x;
        }

        public void setX(String x) {
            this.x = x;
        }

        public String getY() {
            return y;
        }

        public void setY(String y) {
            this.y = y;
        }

        public String getD() {
            return d;
        }

        public void setD(String d) {
            this.d = d;
        }

        public String getAlg() {
            return alg;
        }

        public void setAlg(String alg) {
            this.alg = alg;
        }
    }

}
