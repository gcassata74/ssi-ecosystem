package com.izylife.ssi.config;

import com.izylife.ssi.security.SpidAuthenticationSuccessHandler;
import com.izylife.ssi.service.OnboardingStateService;
import com.izylife.ssi.service.SpidAttributeMapper;
import com.izylife.ssi.service.SpidAuthnRequestStore;
import org.opensaml.core.xml.XMLObjectBuilderFactory;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.impl.XSAnyBuilder;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallingException;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnContextComparisonTypeEnumeration;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Extensions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;
import org.opensaml.saml.saml2.core.impl.AuthnContextClassRefBuilder;
import org.opensaml.saml.saml2.core.impl.ExtensionsBuilder;
import org.opensaml.saml.saml2.core.impl.NameIDPolicyBuilder;
import org.opensaml.saml.saml2.core.impl.RequestedAuthnContextBuilder;
import org.opensaml.security.SecurityException;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.SignatureSigningParameters;
import org.opensaml.xmlsec.keyinfo.impl.X509KeyInfoGeneratorFactory;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureSupport;
import org.opensaml.xmlsec.signature.support.Signer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.security.saml2.provider.service.web.authentication.OpenSaml4AuthenticationRequestResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import javax.xml.namespace.QName;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.time.Instant;
import java.util.UUID;


@Configuration
@ConditionalOnProperty(prefix = "app.spid", name = "enabled", havingValue = "true")
public class SpidSamlConfiguration {

    private static final String SPID_EXTENSIONS_NAMESPACE = "https://spid.gov.it/saml-extensions";
    private static final String SPID_EXTENSIONS_PREFIX = "spid";
    private static final QName SPID_EXTENSIONS_ELEMENT = new QName(SPID_EXTENSIONS_NAMESPACE, "Extensions", SPID_EXTENSIONS_PREFIX);
    private static final QName SPID_PURPOSE_ELEMENT = new QName(SPID_EXTENSIONS_NAMESPACE, "Purpose", SPID_EXTENSIONS_PREFIX);
    private static final QName SPID_REQUESTED_ATTRIBUTES_ELEMENT = new QName(SPID_EXTENSIONS_NAMESPACE, "RequestedAttributes", SPID_EXTENSIONS_PREFIX);
    private static final QName SPID_REQUESTED_ATTRIBUTE_ELEMENT = new QName(SPID_EXTENSIONS_NAMESPACE, "RequestedAttribute", SPID_EXTENSIONS_PREFIX);
    private static final QName ATTR_NAME = new QName("Name");
    private static final QName ATTR_FRIENDLY_NAME = new QName("FriendlyName");
    private static final QName ATTR_NAME_FORMAT = new QName("NameFormat");
    private static final QName ATTR_IS_REQUIRED = new QName("isRequired");

    private static final Logger LOGGER = LoggerFactory.getLogger(SpidSamlConfiguration.class);

    private final SpidAuthnRequestStore spidAuthnRequestStore;

    public SpidSamlConfiguration(SpidAuthnRequestStore spidAuthnRequestStore) {
        this.spidAuthnRequestStore = spidAuthnRequestStore;
    }

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository(AppProperties appProperties,
                                                                                 ResourceLoader resourceLoader) {
        AppProperties.SpidProperties spid = appProperties.getSpid();
        if (spid == null) {
            throw new IllegalStateException("SPID properties must be configured when app.spid.enabled=true");
        }
        if (spid.getIdentityProviderMetadataLocation() == null) {
            throw new IllegalStateException("app.spid.identity-provider-metadata-location must be supplied");
        }
        if (spid.getSigningKeyLocation() == null || spid.getSigningCertificateLocation() == null) {
            throw new IllegalStateException("Both app.spid.signing-key-location and app.spid.signing-certificate-location must be supplied");
        }

        RelyingPartyRegistration.Builder builder = RelyingPartyRegistrations
                .fromMetadataLocation(spid.getIdentityProviderMetadataLocation());

        builder.registrationId(spid.getRegistrationId());
        if (spid.getEntityId() != null && !spid.getEntityId().isBlank()) {
            builder.entityId(spid.getEntityId());
        }
        if (spid.getAssertionConsumerService() != null && !spid.getAssertionConsumerService().isBlank()) {
            builder.assertionConsumerServiceLocation(spid.getAssertionConsumerService());
        }
        builder.assertionConsumerServiceBinding(Saml2MessageBinding.POST);
        builder.nameIdFormat(NameIDType.TRANSIENT);
        builder.authnRequestsSigned(true);
        builder.assertingPartyDetails(details -> {
            if (spid.getSingleSignOnServiceLocation() != null && !spid.getSingleSignOnServiceLocation().isBlank()) {
                details.singleSignOnServiceLocation(spid.getSingleSignOnServiceLocation());
            }
            details.singleSignOnServiceBinding(Saml2MessageBinding.REDIRECT);
            details.wantAuthnRequestsSigned(true);
        });
        if (spid.getSingleLogoutServiceLocation() != null && !spid.getSingleLogoutServiceLocation().isBlank()) {
            builder.singleLogoutServiceLocation(spid.getSingleLogoutServiceLocation());
        }

        Saml2X509Credential signingCredential = buildSigningCredential(spid, resourceLoader);
        Saml2X509Credential decryptionCredential = buildDecryptionCredential(spid, resourceLoader);
        builder.signingX509Credentials(credentials -> credentials.add(signingCredential));
        builder.decryptionX509Credentials(credentials -> credentials.add(decryptionCredential));

        RelyingPartyRegistration registration = builder.build();
        return new InMemoryRelyingPartyRegistrationRepository(registration);
    }

    @Bean
    public OpenSaml4AuthenticationRequestResolver spidAuthenticationRequestResolver(RelyingPartyRegistrationRepository repository,
                                                                                    AppProperties appProperties) {
        OpenSaml4AuthenticationRequestResolver resolver = new OpenSaml4AuthenticationRequestResolver(repository);
        resolver.setAuthnRequestCustomizer(context -> {
            customizeAuthnRequest(context, appProperties.getSpid());
            if (spidAuthnRequestStore != null) {
                AuthnRequest authnRequest = context.getAuthnRequest();
                if (authnRequest != null) {
                    spidAuthnRequestStore.update(serialize(authnRequest));
                }
            }
        });
        return resolver;
    }

    @Bean
    public SpidAuthenticationSuccessHandler spidAuthenticationSuccessHandler(OnboardingStateService onboardingStateService,
                                                                             SpidAttributeMapper attributeMapper,
                                                                             AppProperties appProperties) {
        return new SpidAuthenticationSuccessHandler(onboardingStateService, attributeMapper, appProperties);
    }

    private void customizeAuthnRequest(OpenSaml4AuthenticationRequestResolver.AuthnRequestContext context,
                                       AppProperties.SpidProperties spid) {
        if (spid == null) {
            return;
        }

        AuthnRequest authnRequest = context.getAuthnRequest();
        if (authnRequest.getID() == null || authnRequest.getID().isBlank()) {
            authnRequest.setID("_" + UUID.randomUUID());
        }
        authnRequest.setIssueInstant(Instant.now());
        authnRequest.setForceAuthn(Boolean.TRUE);
        authnRequest.setProtocolBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        String destination = context.getRelyingPartyRegistration()
                .getAssertingPartyDetails()
                .getSingleSignOnServiceLocation();
        if (spid.getSingleSignOnServiceLocation() != null && !spid.getSingleSignOnServiceLocation().isBlank()) {
            destination = spid.getSingleSignOnServiceLocation();
        }
        authnRequest.setDestination(destination);

        Integer attributeIndex = spid.getAttributeConsumingServiceIndex();
        if (attributeIndex == null) {
            attributeIndex = 1;
        }
        authnRequest.setAttributeConsumingServiceIndex(attributeIndex);

        Issuer issuer = authnRequest.getIssuer();
        if (issuer != null) {
            issuer.setFormat(NameIDType.ENTITY);
            String entityId = resolveEntityId(spid, context.getRelyingPartyRegistration());
            if (entityId != null && !entityId.isBlank()) {
                issuer.setValue(entityId);
            }
            issuer.setNameQualifier(null);
        }

        NameIDPolicy nameIdPolicy = ensureNameIdPolicy(authnRequest);
        nameIdPolicy.setFormat(NameIDType.TRANSIENT);
        nameIdPolicy.setAllowCreate(Boolean.TRUE);

        RequestedAuthnContext requestedAuthnContext = ensureRequestedAuthnContext(authnRequest);
        requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.EXACT);
        requestedAuthnContext.getAuthnContextClassRefs().clear();

        List<String> requestedLevels = spid.getRequestedAuthnContextClassRefs();
        LinkedHashSet<String> authnLevels = new LinkedHashSet<>();
        if (requestedLevels != null) {
            for (String candidate : requestedLevels) {
                if (candidate == null) {
                    continue;
                }
                String trimmed = candidate.trim();
                if (!trimmed.isEmpty()) {
                    authnLevels.add(trimmed);
                }
            }
        }
        if (authnLevels.isEmpty()) {
            authnLevels.add("https://www.spid.gov.it/SpidL2");
        }
        AuthnContextClassRefBuilder classRefBuilder = new AuthnContextClassRefBuilder();
        for (String value : authnLevels) {
            AuthnContextClassRef reference = classRefBuilder.buildObject();
            reference.setURI(value);
            requestedAuthnContext.getAuthnContextClassRefs().add(reference);
        }

        applySpidExtensions(authnRequest, spid);

        signAuthnRequest(authnRequest, context.getRelyingPartyRegistration());

        LOGGER.info("SPID AuthnRequest built for destination {}:\n{}",
                authnRequest.getDestination(),
                serialize(authnRequest));
    }

    private NameIDPolicy ensureNameIdPolicy(AuthnRequest authnRequest) {
        NameIDPolicy policy = authnRequest.getNameIDPolicy();
        if (policy == null) {
            policy = new NameIDPolicyBuilder().buildObject();
            authnRequest.setNameIDPolicy(policy);
        }
        return policy;
    }

    private RequestedAuthnContext ensureRequestedAuthnContext(AuthnRequest authnRequest) {
        RequestedAuthnContext context = authnRequest.getRequestedAuthnContext();
        if (context == null) {
            context = new RequestedAuthnContextBuilder().buildObject();
            authnRequest.setRequestedAuthnContext(context);
        }
        return context;
    }

    private void applySpidExtensions(AuthnRequest authnRequest, AppProperties.SpidProperties spid) {
        String purpose = spid.getPurpose();
        List<AppProperties.RequestedAttributeProperties> requestedAttributes = spid.getRequestedAttributes();
        boolean hasPurpose = purpose != null && !purpose.isBlank();
        boolean hasRequestedAttributes = requestedAttributes != null && requestedAttributes.stream()
                .anyMatch(attribute -> attribute.getName() != null && !attribute.getName().isBlank());

        if (!hasPurpose && !hasRequestedAttributes) {
            return;
        }

        Extensions extensions = authnRequest.getExtensions();
        if (extensions == null) {
            extensions = new ExtensionsBuilder().buildObject();
            authnRequest.setExtensions(extensions);
        }

        XSAnyBuilder builder = xsAnyBuilder();
        XSAny spidExtensions = builder.buildObject(SPID_EXTENSIONS_ELEMENT);
        boolean hasChildren = false;

        if (hasPurpose) {
            XSAny purposeElement = builder.buildObject(SPID_PURPOSE_ELEMENT);
            purposeElement.setTextContent(purpose);
            spidExtensions.getUnknownXMLObjects().add(purposeElement);
            hasChildren = true;
        }

        if (hasRequestedAttributes) {
            XSAny requestedAttributesElement = builder.buildObject(SPID_REQUESTED_ATTRIBUTES_ELEMENT);
            for (AppProperties.RequestedAttributeProperties attribute : requestedAttributes) {
                if (attribute == null || attribute.getName() == null || attribute.getName().isBlank()) {
                    continue;
                }
                XSAny attributeElement = builder.buildObject(SPID_REQUESTED_ATTRIBUTE_ELEMENT);
                attributeElement.getUnknownAttributes().put(ATTR_NAME, attribute.getName());
                if (attribute.getFriendlyName() != null && !attribute.getFriendlyName().isBlank()) {
                    attributeElement.getUnknownAttributes().put(ATTR_FRIENDLY_NAME, attribute.getFriendlyName());
                }
                String nameFormat = attribute.getNameFormat();
                if (nameFormat == null || nameFormat.isBlank()) {
                    nameFormat = "urn:oasis:names:tc:SAML:2.0:attrname-format:basic";
                }
                attributeElement.getUnknownAttributes().put(ATTR_NAME_FORMAT, nameFormat);
                attributeElement.getUnknownAttributes().put(ATTR_IS_REQUIRED, Boolean.toString(attribute.isRequired()));
                requestedAttributesElement.getUnknownXMLObjects().add(attributeElement);
            }
            if (!requestedAttributesElement.getUnknownXMLObjects().isEmpty()) {
                spidExtensions.getUnknownXMLObjects().add(requestedAttributesElement);
                hasChildren = true;
            }
        }

        if (hasChildren) {
            extensions.getUnknownXMLObjects().add(spidExtensions);
        }
    }

    private XSAnyBuilder xsAnyBuilder() {
        XMLObjectBuilderFactory builderFactory = XMLObjectProviderRegistrySupport.getBuilderFactory();
        XSAnyBuilder builder = (XSAnyBuilder) builderFactory.getBuilder(XSAny.TYPE_NAME);
        if (builder == null) {
            throw new IllegalStateException("Unable to obtain XSAnyBuilder from OpenSAML registry");
        }
        return builder;
    }

    private String resolveEntityId(AppProperties.SpidProperties spid, RelyingPartyRegistration registration) {
        String candidate = spid != null ? spid.getEntityId() : null;
        if (candidate == null || candidate.isBlank()) {
            candidate = registration != null ? registration.getEntityId() : null;
        }
        return (candidate != null && !candidate.isBlank()) ? candidate : null;
    }

    private void signAuthnRequest(AuthnRequest authnRequest, RelyingPartyRegistration registration) {
        if (authnRequest == null || registration == null) {
            return;
        }
        Collection<Saml2X509Credential> signingCredentials = registration.getSigningX509Credentials();
        if (signingCredentials == null || signingCredentials.isEmpty()) {
            LOGGER.warn("Skipping AuthnRequest signature: no signing credentials for registration {}", registration.getRegistrationId());
            return;
        }

        Saml2X509Credential credential = signingCredentials.iterator().next();
        Signature signature = (Signature) XMLObjectProviderRegistrySupport.getBuilderFactory()
                .getBuilder(Signature.DEFAULT_ELEMENT_NAME)
                .buildObject(Signature.DEFAULT_ELEMENT_NAME);

        BasicX509Credential basicCredential = new BasicX509Credential(credential.getCertificate(), credential.getPrivateKey());
        SignatureSigningParameters signingParameters = new SignatureSigningParameters();
        signingParameters.setSigningCredential(basicCredential);
        signingParameters.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
        signingParameters.setSignatureReferenceDigestMethod(SignatureConstants.ALGO_ID_DIGEST_SHA256);
        signingParameters.setSignatureCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

        X509KeyInfoGeneratorFactory keyInfoGeneratorFactory = new X509KeyInfoGeneratorFactory();
        keyInfoGeneratorFactory.setEmitEntityCertificate(true);
        signingParameters.setKeyInfoGenerator(keyInfoGeneratorFactory.newInstance());

        try {
            SignatureSupport.prepareSignatureParams(signature, signingParameters);
        } catch (SecurityException ex) {
            LOGGER.error("Unable to prepare AuthnRequest signature parameters", ex);
            return;
        }

        authnRequest.setSignature(signature);

        try {
            Marshaller marshaller = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(authnRequest);
            if (marshaller == null) {
                throw new IllegalStateException("No OpenSAML marshaller for AuthnRequest");
            }
            marshaller.marshall(authnRequest);
            Signer.signObject(signature);
        } catch (MarshallingException | SignatureException | IllegalStateException ex) {
            LOGGER.error("Failed to sign SPID AuthnRequest", ex);
            authnRequest.setSignature(null);
        }
    }

    private String serialize(AuthnRequest authnRequest) {
        try {
            Marshaller marshaller = XMLObjectProviderRegistrySupport.getMarshallerFactory().getMarshaller(authnRequest);
            if (marshaller == null) {
                throw new IllegalStateException("No OpenSAML marshaller for AuthnRequest");
            }
            return SerializeSupport.nodeToString(marshaller.marshall(authnRequest));
        } catch (MarshallingException ex) {
            LOGGER.warn("Unable to serialize SPID AuthnRequest for debugging", ex);
            return "<serialization-error/>";
        }
    }

    private Saml2X509Credential buildSigningCredential(AppProperties.SpidProperties spid, ResourceLoader resourceLoader) {
        try {
            X509Certificate certificate = loadCertificate(resourceLoader.getResource(spid.getSigningCertificateLocation()));
            PrivateKey privateKey = loadPrivateKey(resourceLoader.getResource(spid.getSigningKeyLocation()), certificate.getPublicKey().getAlgorithm());
            return Saml2X509Credential.signing(privateKey, certificate);
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to load SPID signing credentials", ex);
        }
    }

    private Saml2X509Credential buildDecryptionCredential(AppProperties.SpidProperties spid, ResourceLoader resourceLoader) {
        try {
            X509Certificate certificate = loadCertificate(resourceLoader.getResource(spid.getSigningCertificateLocation()));
            PrivateKey privateKey = loadPrivateKey(resourceLoader.getResource(spid.getSigningKeyLocation()), certificate.getPublicKey().getAlgorithm());
            return Saml2X509Credential.decryption(privateKey, certificate);
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to load SPID decryption credentials", ex);
        }
    }

    private X509Certificate loadCertificate(Resource resource) throws IOException, CertificateException {
        try (InputStream inputStream = resource.getInputStream()) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(inputStream);
        }
    }

    private PrivateKey loadPrivateKey(Resource resource, String algorithm) throws IOException, GeneralSecurityException {
        String pem = readAll(resource);
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        try {
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
        } catch (InvalidKeySpecException ex) {
            throw new IllegalStateException("The provided private key must be in PKCS#8 format", ex);
        }
    }

    private String readAll(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
