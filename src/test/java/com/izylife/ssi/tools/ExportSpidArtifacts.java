package com.izylife.ssi.tools;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.config.SpidSamlConfiguration;
import com.izylife.ssi.controller.SpidAuthnRequestController;
import com.izylife.ssi.controller.SpidMetadataController;
import com.izylife.ssi.service.SpidAuthnRequestStore;
import org.opensaml.core.config.InitializationService;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.authentication.OpenSaml4AuthenticationRequestResolver;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.Deflater;

public final class ExportSpidArtifacts {

    private static final Path OUTPUT_DIR = Path.of("build", "spid-export");
    private static final Path METADATA_PATH = OUTPUT_DIR.resolve("metadata.xml");
    private static final Path AUTHN_REQUEST_PATH = OUTPUT_DIR.resolve("authn-request.xml");
    private static final Path AUTHN_REQUEST_BASE64_PATH = OUTPUT_DIR.resolve("authn-request.base64");
    private static final Path AUTHN_REQUEST_REDIRECT_PATH = OUTPUT_DIR.resolve("authn-request-redirect.txt");

    private ExportSpidArtifacts() {
    }

    public static void main(String[] args) throws Exception {
        InitializationService.initialize();

        Files.createDirectories(OUTPUT_DIR);

        AppProperties properties = new AppProperties();
        AppProperties.SpidProperties spid = properties.getSpid();
        spid.setEnabled(true);
        spid.setIdentityProviderMetadataLocation("classpath:spid/mock-idp-metadata.xml");
        spid.setEntityId("https://mica-semicivilized-heavily.ngrok-free.dev/spid");
        spid.setAssertionConsumerService("https://mica-semicivilized-heavily.ngrok-free.dev/login/saml2/sso/spid");
        spid.setSingleSignOnServiceLocation("https://demo.spid.gov.it/samlsso");
        spid.setSigningCertificateLocation("classpath:spid/sp-signing-cert.pem");
        spid.setSigningKeyLocation("classpath:spid/sp-signing-key.pem");
        spid.setAuthnRequestOutput(AUTHN_REQUEST_PATH.toString());

        SpidAuthnRequestStore store = new SpidAuthnRequestStore(properties);
        SpidSamlConfiguration configuration = new SpidSamlConfiguration(store);
        RelyingPartyRegistrationRepository repository = configuration.relyingPartyRegistrationRepository(properties, new DefaultResourceLoader());

        SpidMetadataController metadataController = new SpidMetadataController(properties, repository);
        ResponseEntity<String> metadataResponse = metadataController.metadata();
        if (!metadataResponse.getStatusCode().is2xxSuccessful() || metadataResponse.getBody() == null) {
            throw new IllegalStateException("Unable to generate SPID metadata: " + metadataResponse.getStatusCode());
        }
        Files.writeString(METADATA_PATH, metadataResponse.getBody(), StandardCharsets.UTF_8);

        OpenSaml4AuthenticationRequestResolver resolver = configuration.spidAuthenticationRequestResolver(repository, properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", spid.getLoginPath());
        request.setScheme("https");
        request.setServerName("example.org");
        request.setServerPort(443);
        request.setServletPath(spid.getLoginPath());
        resolver.resolve(request);

        SpidAuthnRequestController authnRequestController = new SpidAuthnRequestController(properties, store);
        ResponseEntity<String> authnResponse = authnRequestController.downloadLatestAuthnRequest();
        if (!authnResponse.getStatusCode().is2xxSuccessful() || authnResponse.getBody() == null) {
            throw new IllegalStateException("Unable to capture SPID AuthnRequest: " + authnResponse.getStatusCode());
        }
        byte[] authnRequestBytes = authnResponse.getBody().getBytes(StandardCharsets.UTF_8);
        Files.writeString(AUTHN_REQUEST_PATH, authnResponse.getBody(), StandardCharsets.UTF_8);
        Files.writeString(AUTHN_REQUEST_BASE64_PATH,
                Base64.getEncoder().encodeToString(authnRequestBytes),
                StandardCharsets.UTF_8);

        var registration = repository.findByRegistrationId(spid.getRegistrationId());
        if (registration == null) {
            throw new IllegalStateException("Unable to load relying party registration for " + spid.getRegistrationId());
        }
        String redirectUrl = buildRedirectRequest(authnRequestBytes, spid, registration.getSigningX509Credentials().iterator().next().getPrivateKey());
        Files.writeString(AUTHN_REQUEST_REDIRECT_PATH, redirectUrl, StandardCharsets.UTF_8);

        System.out.println("Metadata exported to: " + METADATA_PATH.toAbsolutePath());
        System.out.println("AuthnRequest exported to: " + AUTHN_REQUEST_PATH.toAbsolutePath());
        System.out.println("AuthnRequest (Base64) exported to: " + AUTHN_REQUEST_BASE64_PATH.toAbsolutePath());
        System.out.println("HTTP-Redirect URL exported to: " + AUTHN_REQUEST_REDIRECT_PATH.toAbsolutePath());
    }

    private static String buildRedirectRequest(byte[] authnRequestBytes,
                                               AppProperties.SpidProperties spid,
                                               PrivateKey signingKey) throws Exception {
        String destination = spid.getSingleSignOnServiceLocation();
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException("SingleSignOnServiceLocation must be configured to build redirect request");
        }

        String samlRequest = deflateAndEncode(authnRequestBytes);
        String relayState = UUID.randomUUID().toString();
        String sigAlg = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";

        String encodedRequest = urlEncode(samlRequest);
        String encodedRelayState = urlEncode(relayState);
        String encodedSigAlg = urlEncode(sigAlg);

        String unsignedPayload = "SAMLRequest=" + encodedRequest
                + "&RelayState=" + encodedRelayState
                + "&SigAlg=" + encodedSigAlg;

        String signature = signRedirect(unsignedPayload, signingKey);
        String encodedSignature = urlEncode(signature);

        return destination + "?" + unsignedPayload + "&Signature=" + encodedSignature;
    }

    private static String deflateAndEncode(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[4096];
        int len;
        try (var output = new java.io.ByteArrayOutputStream()) {
            while (!deflater.finished()) {
                len = deflater.deflate(buffer);
                output.write(buffer, 0, len);
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to deflate AuthnRequest", ex);
        }
    }

    private static String signRedirect(String payload, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        byte[] signed = signature.sign();
        return Base64.getEncoder().encodeToString(signed);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
