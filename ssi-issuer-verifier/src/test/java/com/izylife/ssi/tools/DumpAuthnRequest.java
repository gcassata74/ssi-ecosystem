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

package com.izylife.ssi.tools;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.config.SpidSamlConfiguration;
import com.izylife.ssi.service.SpidAuthnRequestStore;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.saml2.provider.service.authentication.AbstractSaml2AuthenticationRequest;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.security.saml2.provider.service.web.authentication.OpenSaml4AuthenticationRequestResolver;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class DumpAuthnRequest {

    private DumpAuthnRequest() {
    }

    public static void main(String[] args) throws Exception {
        InitializationService.initialize();

        AppProperties properties = new AppProperties();
        AppProperties.SpidProperties spid = properties.getSpid();
        spid.setEnabled(true);
        spid.setIdentityProviderMetadataLocation("classpath:spid/mock-idp-metadata.xml");
        spid.setEntityId("https://izylife-issuer.eu.ngrok.io/spid");
        spid.setAssertionConsumerService("https://izylife-issuer.eu.ngrok.io/login/saml2/sso/spid");
        spid.setSingleSignOnServiceLocation("https://demo.spid.gov.it/samlsso");
        SpidTestCredentials.requireConfigured(spid);

        SpidAuthnRequestStore store = new SpidAuthnRequestStore(properties);
        SpidSamlConfiguration configuration = new SpidSamlConfiguration(store);
        RelyingPartyRegistrationRepository repository = configuration.relyingPartyRegistrationRepository(properties, new DefaultResourceLoader());

        OpenSaml4AuthenticationRequestResolver resolver = configuration.spidAuthenticationRequestResolver(repository, properties);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/saml2/authenticate/spid");
        request.setScheme("https");
        request.setServerName("example.org");
        request.setServerPort(443);
        request.setServletPath("/saml2/authenticate/spid");

        AbstractSaml2AuthenticationRequest authenticationRequest = resolver.resolve(request);
        Saml2MessageBinding binding = authenticationRequest.getBinding();
        System.out.println("Binding: " + binding);

        byte[] message = Base64.getDecoder().decode(authenticationRequest.getSamlRequest());
        String xml = new String(message, StandardCharsets.UTF_8);
        System.out.println("Raw:\n" + xml);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(message));
        AuthnRequest authnRequest = (AuthnRequest) XMLObjectProviderRegistrySupport.getUnmarshallerFactory()
                .getUnmarshaller(document.getDocumentElement())
                .unmarshall(document.getDocumentElement());
        System.out.println("Pretty:\n" + SerializeSupport.prettyPrintXML(toElement(authnRequest)));
    }

    private static org.w3c.dom.Element toElement(AuthnRequest authnRequest) throws MarshallingException {
        Marshaller marshaller = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(authnRequest);
        if (marshaller == null) {
            throw new IllegalStateException("No marshaller for AuthnRequest");
        }
        return marshaller.marshall(authnRequest);
    }
}
