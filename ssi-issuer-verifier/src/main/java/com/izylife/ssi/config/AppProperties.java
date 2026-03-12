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
    private SpidProperties spid = new SpidProperties();

    @Getter
    @Setter
    public static class IssuerProperties {
        private String organizationName;
        private String endpoint;
        private String credentialIssuerId;
        private String credentialOfferUri;
        private SigningKeyProperties signingKey = new SigningKeyProperties();
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

    @Getter
    @Setter
    public static class SpidProperties {
        private boolean enabled;
        private String registrationId = "spid";
        private String entityId;
        private String assertionConsumerService;
        private String singleSignOnServiceLocation;
        private String singleLogoutServiceLocation;
        private String signingCertificateLocation;
        private String signingKeyLocation;
        private String identityProviderMetadataLocation;
        private String identityProviderEntityId = "https://demo.spid.gov.it";
        private String authnRequestOutput;
        private String loginPath = "/saml2/authenticate/spid";
        private String postLoginRedirect = "/issuer";
        private String serviceNameIt = "Servizio Izylife";
        private String serviceNameEn = "Izylife Service";
        private String organizationNameIt = "Izylife Solutions S.R.L.";
        private String organizationDisplayNameIt = "Izylife";
        private String organizationUrlIt = "https://www.izylife.com";
        private String organizationNameEn = "Izylife Solutions S.R.L.";
        private String organizationDisplayNameEn = "Izylife";
        private String organizationUrlEn = "https://www.izylife.com";
        private String contactCompany = "Izylife Solutions S.R.L.";
        private String contactEmail = "info@izylifesolutions.com";
        private String administrativeEmail = "admin@izylife.com";
        private String administrativeTelephone = "+39-010-1234567";
        private String technicalEmail = "tech@izylife.com";
        private String technicalTelephone = "+39-010-1234567";
        private String purpose = "Accesso area riservata Izylife";
        private String purposeEn = "Izylife restricted area access";
        private Integer attributeConsumingServiceIndex = 1;
        private List<String> requestedAuthnContextClassRefs = List.of("https://www.spid.gov.it/SpidL2");
        private String requestedAuthnContextComparison = "exact";
        private List<RequestedAttributeProperties> requestedAttributes = defaultRequestedAttributes();

        private static List<RequestedAttributeProperties> defaultRequestedAttributes() {
            RequestedAttributeProperties fiscalNumber = new RequestedAttributeProperties();
            fiscalNumber.setName("fiscalNumber");
            fiscalNumber.setFriendlyName("fiscalNumber");
            fiscalNumber.setRequired(true);

            RequestedAttributeProperties name = new RequestedAttributeProperties();
            name.setName("name");
            name.setFriendlyName("name");
            name.setRequired(true);

            RequestedAttributeProperties familyName = new RequestedAttributeProperties();
            familyName.setName("familyName");
            familyName.setFriendlyName("familyName");
            familyName.setRequired(true);

            RequestedAttributeProperties email = new RequestedAttributeProperties();
            email.setName("email");
            email.setFriendlyName("email");

            return List.of(fiscalNumber, name, familyName, email);
        }
    }

    @Getter
    @Setter
    public static class RequestedAttributeProperties {
        private String name;
        private String friendlyName;
        private String nameFormat = "urn:oasis:names:tc:SAML:2.0:attrname-format:basic";
        private boolean required;
    }
}
