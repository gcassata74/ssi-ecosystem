package com.izylife.ssi.controller;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.config.AppProperties.SpidProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;

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
            String metadata = buildMetadata(spid, registration);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(metadata);
        } catch (IllegalStateException ex) {
            LOGGER.error("Unable to build SPID metadata", ex);
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body("Unable to generate SPID metadata");
        }
    }

    private String buildMetadata(SpidProperties spid, RelyingPartyRegistration registration) {
        String entityId = firstNonBlank(spid.getEntityId(), registration.getEntityId());
        String acsLocation = firstNonBlank(spid.getAssertionConsumerService(), registration.getAssertionConsumerServiceLocation());
        String sloLocation = firstNonBlank(spid.getSingleLogoutServiceLocation(), deriveSingleLogout(entityId));

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
        String administrativeEmail = firstNonBlank(spid.getAdministrativeEmail(), "mailto:admin@izylife.com");
        String administrativeTelephone = firstNonBlank(spid.getAdministrativeTelephone(), "+39-010-1234567");
        String technicalEmail = firstNonBlank(spid.getTechnicalEmail(), "mailto:tech@izylife.com");
        String technicalTelephone = firstNonBlank(spid.getTechnicalTelephone(), "+39-010-1234567");

        return ("""
                <EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata"
                                  xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                                  entityID="%s">
                  <SPSSODescriptor AuthnRequestsSigned="true" WantAssertionsSigned="true"
                      protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">

                    <KeyDescriptor use="signing">
                      <ds:KeyInfo><ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data></ds:KeyInfo>
                    </KeyDescriptor>
                    <KeyDescriptor use="encryption">
                      <ds:KeyInfo><ds:X509Data><ds:X509Certificate>%s</ds:X509Certificate></ds:X509Data></ds:KeyInfo>
                    </KeyDescriptor>

                    <AssertionConsumerService
                        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
                        Location="%s"
                        index="0" isDefault="true"/>

                    <SingleLogoutService
                        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
                        Location="%s"/>

                    <NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:transient</NameIDFormat>

                    <AttributeConsumingService index="1" isDefault="true">
                      <ServiceName xml:lang="it">%s</ServiceName>
                      <ServiceName xml:lang="en">%s</ServiceName>
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
                </EntityDescriptor>
                """
                ).formatted(
                entityId,
                signingCertificate,
                encryptionCertificate,
                acsLocation,
                sloLocation,
                serviceNameIt,
                serviceNameEn,
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
                technicalTelephone
        );
    }

    private String firstNonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private String deriveSingleLogout(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return "";
        }
        return entityId.endsWith("/") ? entityId + "logout" : entityId + "/logout";
    }

    private String encodeCertificate(X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (CertificateEncodingException ex) {
            throw new IllegalStateException("Unable to encode certificate", ex);
        }
    }
}
