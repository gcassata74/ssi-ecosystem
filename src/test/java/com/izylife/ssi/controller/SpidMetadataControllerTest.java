package com.izylife.ssi.controller;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.config.SpidSamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SpidMetadataControllerTest {

    @BeforeAll
    static void initializeOpenSaml() throws Exception {
        InitializationService.initialize();
    }

    @Test
    void metadataSignatureIsValid() throws Exception {
        AppProperties properties = new AppProperties();
        AppProperties.SpidProperties spid = properties.getSpid();
        spid.setEnabled(true);
        spid.setIdentityProviderMetadataLocation("classpath:spid/mock-idp-metadata.xml");
        spid.setSigningCertificateLocation("classpath:spid/sp-signing-cert.pem");
        spid.setSigningKeyLocation("classpath:spid/sp-signing-key.pem");

        SpidSamlConfiguration configuration = new SpidSamlConfiguration();
        RelyingPartyRegistrationRepository repository = configuration.relyingPartyRegistrationRepository(properties, new DefaultResourceLoader());
        SpidMetadataController controller = new SpidMetadataController(properties, repository);

        ResponseEntity<String> response = controller.metadata();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotBlank();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(response.getBody().getBytes()));

        Unmarshaller unmarshaller = XMLObjectProviderRegistrySupport.getUnmarshallerFactory()
                .getUnmarshaller(document.getDocumentElement());
        assertThat(unmarshaller).isNotNull();
        EntityDescriptor descriptor = (EntityDescriptor) unmarshaller.unmarshall(document.getDocumentElement());

        Signature signature = descriptor.getSignature();
        assertThat(signature).as("metadata signature").isNotNull();

        var registration = repository.findByRegistrationId(spid.getRegistrationId());
        var signingCredential = registration.getSigningX509Credentials().iterator().next();
        BasicX509Credential credential = new BasicX509Credential(signingCredential.getCertificate());

        SignatureValidator.validate(signature, credential);
    }
}
