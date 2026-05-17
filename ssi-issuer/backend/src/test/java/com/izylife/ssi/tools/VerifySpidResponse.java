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

import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class VerifySpidResponse {

    private VerifySpidResponse() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: mvn -Dexec.mainClass=" + VerifySpidResponse.class.getName() +
                    " -Dexec.args=\"/path/to/SAMLResponse.b64 [/path/to/idp-metadata.xml]\"");
            System.exit(1);
        }

        Path responsePath = Path.of(args[0]);
        Path metadataPath = args.length > 1
                ? Path.of(args[1])
                : Path.of("src/main/resources/spid/demo-idp-metadata.xml");

        if (!Files.exists(responsePath)) {
            throw new IllegalArgumentException("SAML Response file not found: " + responsePath);
        }
        if (!Files.exists(metadataPath)) {
            throw new IllegalArgumentException("IdP metadata file not found: " + metadataPath);
        }

        InitializationService.initialize();

        byte[] samlResponseBytes = Files.readAllBytes(responsePath);
        String samlResponseBase64 = new String(samlResponseBytes, StandardCharsets.UTF_8).trim();
        byte[] decodedResponse = Base64.getDecoder().decode(samlResponseBase64);

        EntityDescriptor idpMetadata = loadMetadata(metadataPath);
        BasicX509Credential idpCredential = extractSigningCredential(idpMetadata);

        Response response = unmarshallResponse(decodedResponse);

        validateSignatures(response, idpCredential);

        Map<String, LinkedHashSet<String>> attributes = extractAttributes(response);

        System.out.println("SAML Response signature valid.");
        System.out.println("Issuer: " + response.getIssuer().getValue());
        if (!response.getAssertions().isEmpty()) {
            Assertion assertion = response.getAssertions().get(0);
            System.out.println("Subject NameID: " + (assertion.getSubject() != null && assertion.getSubject().getNameID() != null
                    ? assertion.getSubject().getNameID().getValue()
                    : "<none>"));
        }

        System.out.println("Extracted attributes:");
        attributes.forEach((name, values) -> System.out.println("  - " + name + ": " + values));

        String credentialOffer = buildCredentialOffer(attributes);
        System.out.println("\nSuggested credential offer payload:\n" + credentialOffer);
    }

    private static EntityDescriptor loadMetadata(Path metadataPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(Files.newInputStream(metadataPath));
        Unmarshaller unmarshaller = XMLObjectProviderRegistrySupport.getUnmarshallerFactory()
                .getUnmarshaller(document.getDocumentElement());
        if (unmarshaller == null) {
            throw new IllegalStateException("No unmarshaller for EntityDescriptor");
        }
        return (EntityDescriptor) unmarshaller.unmarshall(document.getDocumentElement());
    }

    private static BasicX509Credential extractSigningCredential(EntityDescriptor descriptor) throws Exception {
        IDPSSODescriptor idpDescriptor = descriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS);
        if (idpDescriptor == null) {
            throw new IllegalStateException("Metadata missing IDPSSODescriptor");
        }
        for (KeyDescriptor keyDescriptor : idpDescriptor.getKeyDescriptors()) {
            if (keyDescriptor.getKeyInfo() == null || keyDescriptor.getKeyInfo().getX509Datas().isEmpty()) {
                continue;
            }
            if (keyDescriptor.getUse() != null && keyDescriptor.getUse() != org.opensaml.security.credential.UsageType.SIGNING) {
                continue;
            }
            String certValue = keyDescriptor.getKeyInfo()
                    .getX509Datas().get(0)
                    .getX509Certificates()
                    .get(0)
                    .getValue()
                    .replaceAll("\\s", "");
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(Base64.getDecoder().decode(certValue)));
            return new BasicX509Credential(certificate);
        }
        throw new IllegalStateException("No signing certificate found in metadata");
    }

    private static Response unmarshallResponse(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
        Unmarshaller unmarshaller = XMLObjectProviderRegistrySupport.getUnmarshallerFactory()
                .getUnmarshaller(document.getDocumentElement());
        if (unmarshaller == null) {
            throw new IllegalStateException("Unable to obtain unmarshaller for Response");
        }
        return (Response) unmarshaller.unmarshall(document.getDocumentElement());
    }

    private static void validateSignatures(Response response, BasicX509Credential credential) throws Exception {
        boolean signatureFound = false;
        Signature responseSignature = response.getSignature();
        if (responseSignature != null) {
            SignatureValidator.validate(responseSignature, credential);
            signatureFound = true;
        }
        for (Assertion assertion : response.getAssertions()) {
            if (assertion.getSignature() != null) {
                SignatureValidator.validate(assertion.getSignature(), credential);
                signatureFound = true;
            }
        }
        if (!signatureFound) {
            throw new IllegalStateException("No signature found on Response or Assertions");
        }
    }

    private static Map<String, LinkedHashSet<String>> extractAttributes(Response response) {
        Map<String, LinkedHashSet<String>> attributes = new LinkedHashMap<>();
        for (Assertion assertion : response.getAssertions()) {
            for (AttributeStatement statement : assertion.getAttributeStatements()) {
                for (Attribute attribute : statement.getAttributes()) {
                    String name = attribute.getName();
                    LinkedHashSet<String> values = attributes.computeIfAbsent(name, k -> new LinkedHashSet<>());
                    attribute.getAttributeValues().forEach(xmlObject -> values.add(normalize(xmlObject)));
                }
            }
        }
        return attributes;
    }

    private static String normalize(XMLObject xmlObject) {
        if (xmlObject == null) {
            return "";
        }
        if (xmlObject.getDOM() != null && xmlObject.getDOM().getTextContent() != null) {
            return xmlObject.getDOM().getTextContent();
        }
        try {
            XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
            var marshaller = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(xmlObject);
            if (marshaller != null) {
                return SerializeSupport.nodeToString(marshaller.marshall(xmlObject));
            }
        } catch (Exception ex) {
            // ignore and fallback
        }
        return xmlObject.toString();
    }

    private static String buildCredentialOffer(Map<String, LinkedHashSet<String>> attributes) {
        String subject = attributes.getOrDefault("fiscalNumber", new LinkedHashSet<>(java.util.List.of("unknown")))
                .iterator().next();
        StringBuilder builder = new StringBuilder();
        builder.append("{")
                .append("\n  \"type\": \"izylife-spid-profile\",")
                .append("\n  \"subject\": \"").append(subject).append("\",")
                .append("\n  \"claims\": {");
        boolean first = true;
        for (Map.Entry<String, LinkedHashSet<String>> entry : attributes.entrySet()) {
            if (!first) {
                builder.append(",");
            }
            builder.append("\n    \"")
                    .append(entry.getKey())
                    .append("\": ");
            if (entry.getValue().size() == 1) {
                builder.append("\"").append(entry.getValue().iterator().next()).append("\"");
            } else {
                builder.append("[");
                boolean innerFirst = true;
                for (String value : entry.getValue()) {
                    if (!innerFirst) {
                        builder.append(", ");
                    }
                    builder.append("\"").append(value).append("\"");
                    innerFirst = false;
                }
                builder.append("]");
            }
            first = false;
        }
        builder.append("\n  }")
                .append("\n}");
        return builder.toString();
    }
}
