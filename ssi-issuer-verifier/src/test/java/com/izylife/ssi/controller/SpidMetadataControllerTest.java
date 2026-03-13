/*
 * SSI Issuer Verifier
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.ssi.controller;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.config.SpidSamlConfiguration;
import com.izylife.ssi.service.SpidAuthnRequestStore;
import com.izylife.ssi.tools.SpidTestCredentials;
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
import java.util.Optional;

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
        SpidTestCredentials.assumeConfigured(spid);

        SpidAuthnRequestStore store = new SpidAuthnRequestStore(properties);
        SpidSamlConfiguration configuration = new SpidSamlConfiguration(store);
        RelyingPartyRegistrationRepository repository = configuration.relyingPartyRegistrationRepository(properties, new DefaultResourceLoader());
        SpidMetadataController controller = new SpidMetadataController(properties, Optional.of(repository));

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
