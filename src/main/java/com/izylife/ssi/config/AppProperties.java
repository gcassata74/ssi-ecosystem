package com.izylife.ssi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private IssuerProperties issuer = new IssuerProperties();
    private VerifierProperties verifier = new VerifierProperties();
    private CorsProperties cors = new CorsProperties();

    @Getter
    @Setter
    public static class IssuerProperties {
        private String organizationName;
        private String endpoint;
        private String qrPayload;
    }

    @Getter
    @Setter
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
    }

    @Getter
    @Setter
    public static class CorsProperties {
        private List<String> allowedOrigins = List.of("*");
        private boolean allowCredentials;
    }

    @Getter
    @Setter
    public static class SigningKeyProperties {
        private String kid;
        private String kty;
        private String crv;
        private String x;
        private String y;
        private String d;
        private String alg = "ES256";
    }
}
