package com.izylife.ssi.controller;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.config.AppProperties.SpidProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.StringReader;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureSupport;

@RestController
@RequestMapping("/spid")
public class SpidMetadataController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpidMetadataController.class);

    private final AppProperties appProperties;
    private final RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    public SpidMetadataController(AppProperties appProperties,
                                  RelyingPartyRegistrationRepository relyingPartyRegistrationRepository) {
        this.appProperties = appProperties;
        this.relyingPartyRegistrationRepository = relyingPartyRegistrationRepository;
    }

    @GetMapping(value = "/metadata", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> metadata() {
        SpidProperties spid = appProperties.getSpid();
        if (spid == null || !spid.isEnabled()) {
            return ResponseEntity.notFound().build();
        }

        String registrationId = spid.getRegistrationId();
        RelyingPartyRegistration registration = relyingPartyRegistrationRepository.findByRegistrationId(registrationId);
        if (registration == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Saml2X509Credential signingCredential = registration.getSigningX509Credentials().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No signing credential available for SPID metadata"));

            String metadata = buildMetadata(spid, registration);
            String signedMetadata = signMetadata(metadata, signingCredential);

            LOGGER.info("Generated SPID metadata for entity {}:\n{}",
                    spid.getEntityId() != null && !spid.getEntityId().isBlank() ? spid.getEntityId() : registration.getEntityId(),
                    signedMetadata);

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(signedMetadata);
        } catch (IllegalStateException ex) {
            LOGGER.error("Unable to build SPID metadata", ex);
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body("Unable to generate SPID metadata");
        }
    }

    private String buildMetadata(SpidProperties spid, RelyingPartyRegistration registration) {
        String entityId = firstNonBlank(spid.getEntityId(), registration.getEntityId());
        String acsLocation = firstNonBlank(spid.getAssertionConsumerService(), registration.getAssertionConsumerServiceLocation());
        String sloLocation = firstNonBlank(spid.getSingleLogoutServiceLocation(), deriveSingleLogout(entityId, acsLocation));
        String metadataId = "_" + UUID.randomUUID();

        String signingCertificate = registration.getSigningX509Credentials().stream()
                .findFirst()
                .map(Saml2X509Credential::getCertificate)
                .map(this::encodeCertificate)
                .orElseThrow(() -> new IllegalStateException("No signing certificate configured"));

        String encryptionCertificate = registration.getDecryptionX509Credentials().stream()
                .findFirst()
                .map(Saml2X509Credential::getCertificate)
                .map(this::encodeCertificate)
                .orElseThrow(() -> new IllegalStateException("No decryption certificate configured"));

        String serviceNameIt = firstNonBlank(spid.getServiceNameIt(), "Servizio Izylife");
        String serviceNameEn = firstNonBlank(spid.getServiceNameEn(), "Izylife Service");
        String organizationNameIt = firstNonBlank(spid.getOrganizationNameIt(), "Izylife Solutions S.R.L.");
        String organizationDisplayNameIt = firstNonBlank(spid.getOrganizationDisplayNameIt(), "Izylife");
        String organizationUrlIt = firstNonBlank(spid.getOrganizationUrlIt(), "https://www.izylife.com");
        String organizationNameEn = firstNonBlank(spid.getOrganizationNameEn(), "Izylife Solutions S.R.L.");
        String organizationDisplayNameEn = firstNonBlank(spid.getOrganizationDisplayNameEn(), "Izylife");
        String organizationUrlEn = firstNonBlank(spid.getOrganizationUrlEn(), "https://www.izylife.com");
        String contactCompany = firstNonBlank(spid.getContactCompany(), organizationNameIt);
        String contactEmail = normalizeEmail(firstNonBlank(spid.getContactEmail(), "info@izylifesolutions.com"));
        String administrativeEmail = normalizeEmail(firstNonBlank(spid.getAdministrativeEmail(), "admin@izylife.com"));
        String administrativeTelephone = firstNonBlank(spid.getAdministrativeTelephone(), "+39-010-1234567");
        String technicalEmail = normalizeEmail(firstNonBlank(spid.getTechnicalEmail(), "tech@izylife.com"));
        String technicalTelephone = firstNonBlank(spid.getTechnicalTelephone(), "+39-010-1234567");
        int attributeConsumingServiceIndex = Optional.ofNullable(spid.getAttributeConsumingServiceIndex())
                .map(value -> Math.max(0, value))
                .orElse(1);
        String requestedAttributesXml = buildRequestedAttributes(spid, "      ");
        String serviceDescriptionsXml = buildServiceDescriptions(spid, "      ");

        return ("""
                <EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata"
                                  xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                                  xmlns:spid="https://spid.gov.it/saml-extensions"
                                  entityID="%s"
                                  ID="%s">
                  <SPSSODescriptor AuthnRequestsSigned="true" WantAssertionsSigned="true"
                      protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">

                    <KeyDescriptor use="signing">
                      <ds:KeyInfo><ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data></ds:KeyInfo>
                    </KeyDescriptor>
                    <KeyDescriptor use="encryption">
                      <ds:KeyInfo><ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data></ds:KeyInfo>
                    </KeyDescriptor>
                    <NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:transient</NameIDFormat>
                    <SingleLogoutService
                        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                        Location="%s"/>
                    <AssertionConsumerService
                        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
                        Location="%s"
                        index="0" isDefault="true"/>
                    <AttributeConsumingService index="%d" isDefault="true">
                      <ServiceName xml:lang="it">%s</ServiceName>
                      <ServiceName xml:lang="en">%s</ServiceName>%s%s
                    </AttributeConsumingService>
                  </SPSSODescriptor>

                  <Organization>
                    <OrganizationName xml:lang="it">%s</OrganizationName>
                    <OrganizationDisplayName xml:lang="it">%s</OrganizationDisplayName>
                    <OrganizationURL xml:lang="it">%s</OrganizationURL>
                    <OrganizationName xml:lang="en">%s</OrganizationName>
                    <OrganizationDisplayName xml:lang="en">%s</OrganizationDisplayName>
                    <OrganizationURL xml:lang="en">%s</OrganizationURL>
                  </Organization>

                  <ContactPerson contactType="administrative">
                    <Company>%s</Company>
                    <EmailAddress>%s</EmailAddress>
                    <TelephoneNumber>%s</TelephoneNumber>
                  </ContactPerson>
                  <ContactPerson contactType="technical">
                    <Company>%s</Company>
                    <EmailAddress>%s</EmailAddress>
                    <TelephoneNumber>%s</TelephoneNumber>
                  </ContactPerson>
                  <ContactPerson contactType="other">
                    <Company>%s</Company>
                    <EmailAddress>%s</EmailAddress>
                    <Extensions>
                      <spid:Public/>
                    </Extensions>
                  </ContactPerson>
                </EntityDescriptor>
                """
                ).formatted(
                entityId,
                metadataId,
                signingCertificate,
                encryptionCertificate,
                sloLocation,
                acsLocation,
                attributeConsumingServiceIndex,
                serviceNameIt,
                serviceNameEn,
                serviceDescriptionsXml,
                requestedAttributesXml,
                organizationNameIt,
                organizationDisplayNameIt,
                organizationUrlIt,
                organizationNameEn,
                organizationDisplayNameEn,
                organizationUrlEn,
                contactCompany,
                administrativeEmail,
                administrativeTelephone,
                contactCompany,
                technicalEmail,
                technicalTelephone,
                contactCompany,
                contactEmail
        );
    }

    private String buildRequestedAttributes(SpidProperties spid, String indent) {
        var requested = spid.getRequestedAttributes();
        if (requested == null || requested.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (AppProperties.RequestedAttributeProperties attribute : requested) {
            if (attribute == null || attribute.getName() == null || attribute.getName().isBlank()) {
                continue;
            }
            String friendlyName = firstNonBlank(attribute.getFriendlyName(), attribute.getName());
            String nameFormat = firstNonBlank(attribute.getNameFormat(), "urn:oasis:names:tc:SAML:2.0:attrname-format:basic");
            builder.append('\n')
                    .append(indent)
                    .append("<RequestedAttribute")
                    .append(" FriendlyName=\"").append(friendlyName).append("\"")
                    .append(" Name=\"").append(attribute.getName()).append("\"")
                    .append(" NameFormat=\"").append(nameFormat).append("\"")
                    .append(" isRequired=\"").append(attribute.isRequired()).append("\"")
                    .append("/>");
        }
        return builder.toString();
    }

    private String buildServiceDescriptions(SpidProperties spid, String indent) {
        String purposeIt = firstNonBlank(spid.getPurpose(), "");
        String purposeEn = firstNonBlank(spid.getPurposeEn(), purposeIt);

        if (purposeIt.isBlank() && purposeEn.isBlank()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        if (!purposeIt.isBlank()) {
            builder.append('\n')
                    .append(indent)
                    .append("<ServiceDescription xml:lang=\"it\">")
                    .append(purposeIt)
                    .append("</ServiceDescription>");
        }
        if (!purposeEn.isBlank()) {
            builder.append('\n')
                    .append(indent)
                    .append("<ServiceDescription xml:lang=\"en\">")
                    .append(purposeEn)
                    .append("</ServiceDescription>");
        }
        return builder.toString();
    }

    private String signMetadata(String metadataXml, Saml2X509Credential signingCredential) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(metadataXml)));

            String metadataId = document.getDocumentElement().getAttribute("ID");
            if (metadataId == null || metadataId.isBlank()) {
                throw new IllegalStateException("Metadata must declare an ID attribute to support signing");
            }

            var unmarshaller = XMLObjectProviderRegistrySupport.getUnmarshallerFactory().getUnmarshaller(document.getDocumentElement());
            if (unmarshaller == null) {
                throw new IllegalStateException("No OpenSAML unmarshaller available for EntityDescriptor");
            }

            EntityDescriptor descriptor = (EntityDescriptor) unmarshaller.unmarshall(document.getDocumentElement());
            descriptor.setID(metadataId);
            descriptor.releaseDOM();
            descriptor.releaseChildrenDOM(true);

            BasicX509Credential credential = new BasicX509Credential(signingCredential.getCertificate(), signingCredential.getPrivateKey());
            credential.setUsageType(UsageType.SIGNING);

            SignatureSigningParameters signingParameters = new SignatureSigningParameters();
            signingParameters.setSigningCredential(credential);
            signingParameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
            signingParameters.setSignatureCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

            X509KeyInfoGeneratorFactory keyInfoFactory = new X509KeyInfoGeneratorFactory();
            keyInfoFactory.setEmitEntityCertificate(true);
            signingParameters.setKeyInfoGenerator(keyInfoFactory.newInstance());
            signingParameters.setSignatureReferenceDigestMethod(SignatureConstants.ALGO_ID_DIGEST_SHA256);

            SignatureSupport.signObject(descriptor, signingParameters);

            var signedElement = descriptor.getDOM();
            if (signedElement == null) {
                var marshaller = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(descriptor);
                if (marshaller == null) {
                    throw new IllegalStateException("No OpenSAML marshaller available for EntityDescriptor");
                }
                signedElement = marshaller.marshall(descriptor);
            }

            return SerializeSupport.nodeToString(signedElement);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign SPID metadata", ex);
        }
    }

    private String firstNonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private String deriveSingleLogout(String entityId, String acsLocation) {
        String baseUrl = extractBaseUrl(firstNonBlank(acsLocation, entityId));
        if (baseUrl.isBlank()) {
            return "";
        }
        return baseUrl + "/saml2/logout";
    }

    private String encodeCertificate(X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (CertificateEncodingException ex) {
            throw new IllegalStateException("Unable to encode certificate", ex);
        }
    }

    private String extractBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            java.net.URI uri = java.net.URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder()
                    .append(uri.getScheme())
                    .append("://")
                    .append(uri.getHost());
            if (uri.getPort() != -1) {
                builder.append(":").append(uri.getPort());
            }
            return builder.toString();
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Unable to derive base URL from '{}'", url, ex);
            return "";
        }
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.regionMatches(true, 0, "mailto:", 0, 7)) {
            trimmed = trimmed.substring(7);
        }
        return trimmed;
    }
}
