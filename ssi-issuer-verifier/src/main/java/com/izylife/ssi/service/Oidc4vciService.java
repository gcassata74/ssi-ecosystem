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

package com.izylife.ssi.service;

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.dto.oidc4vci.AuthorizationServerMetadata;
import com.izylife.ssi.dto.oidc4vci.CredentialConfiguration;
import com.izylife.ssi.dto.oidc4vci.CredentialDefinition;
import com.izylife.ssi.dto.oidc4vci.CredentialIssuerMetadata;
import com.izylife.ssi.dto.oidc4vci.CredentialSubjectField;
import com.izylife.ssi.dto.oidc4vci.CredentialSubjectMetadata;
import com.izylife.ssi.dto.oidc4vci.DisplayEntry;
import com.izylife.ssi.dto.oidc4vci.ProofTypeMetadata;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Oidc4vciService {

    private static final String STAFF_CREDENTIAL_CONFIGURATION_ID = "public_authority_staff";
    private static final Duration OFFER_TTL = Duration.ofMinutes(30);
    private static final Duration AUTHORIZATION_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration C_NONCE_TTL = Duration.ofMinutes(5);

    private final AppProperties appProperties;
    private final IssuerSigningService issuerSigningService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final Map<String, CredentialOfferRecord> offersById = new ConcurrentHashMap<>();
    private final Map<String, String> issuerStateIndex = new ConcurrentHashMap<>();
    private final Map<String, String> preAuthorizedIndex = new ConcurrentHashMap<>();
    private final Map<String, AuthorizationGrant> authorizationGrants = new ConcurrentHashMap<>();
    private final Map<String, AccessTokenGrant> accessTokens = new ConcurrentHashMap<>();

    public Oidc4vciService(AppProperties appProperties, IssuerSigningService issuerSigningService) {
        this.appProperties = appProperties;
        this.issuerSigningService = issuerSigningService;
    }

    public CredentialOfferRecord createStaffCredentialOffer(StaffProfile profile) {
        String offerId = randomIdentifier();
        String issuerState = randomIdentifier();
        String preAuthorizedCode = randomIdentifier();

        CredentialOfferRecord record = new CredentialOfferRecord(
                offerId,
                issuerState,
                preAuthorizedCode,
                false,
                List.of(STAFF_CREDENTIAL_CONFIGURATION_ID),
                profile,
                Instant.now()
        );
        offersById.put(offerId, record);
        issuerStateIndex.put(issuerState, offerId);
        preAuthorizedIndex.put(preAuthorizedCode, offerId);
        return record;
    }

    public Optional<CredentialOfferRecord> findOfferById(String id) {
        return Optional.ofNullable(offersById.get(id)).filter(this::isOfferActive);
    }

    public Map<String, Object> buildCredentialOffer(CredentialOfferRecord record) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("credential_issuer", resolveCredentialIssuerId());
        offer.put("credential_configuration_ids", new ArrayList<>(record.credentialConfigurationIds()));

        Map<String, Object> grants = new LinkedHashMap<>();
        Map<String, Object> authorizationCode = new LinkedHashMap<>();
        authorizationCode.put("issuer_state", record.issuerState());
        grants.put("authorization_code", authorizationCode);

        Map<String, Object> preAuthorized = new LinkedHashMap<>();
        preAuthorized.put("pre-authorized_code", record.preAuthorizedCode());
        if (record.userPinRequired()) {
            preAuthorized.put("user_pin_required", true);
        }
        grants.put("urn:ietf:params:oauth:grant-type:pre-authorized_code", preAuthorized);

        offer.put("grants", grants);
        return offer;
    }

    public Optional<CredentialOfferRecord> findOfferByIssuerState(String issuerState) {
        String offerId = issuerStateIndex.get(issuerState);
        if (offerId == null) {
            return Optional.empty();
        }
        return findOfferById(offerId);
    }

    public AuthorizationCode issueAuthorizationCode(CredentialOfferRecord offer, AuthorizationRequest request) {
        String code = randomIdentifier();
        issuerStateIndex.remove(offer.issuerState());
        AuthorizationGrant grant = new AuthorizationGrant(
                code,
                offer.offerId(),
                request.clientId(),
                request.redirectUri(),
                Instant.now().plus(AUTHORIZATION_CODE_TTL)
        );
        authorizationGrants.put(code, grant);
        return new AuthorizationCode(code, grant.expiresAt());
    }

    public Optional<CredentialOfferRecord> findOfferByPreAuthorizedCode(String preAuthorizedCode) {
        String offerId = preAuthorizedIndex.get(preAuthorizedCode);
        if (offerId == null) {
            return Optional.empty();
        }
        return findOfferById(offerId);
    }

    public Optional<CredentialOfferRecord> consumePreAuthorizedCode(String preAuthorizedCode) {
        String offerId = preAuthorizedIndex.remove(preAuthorizedCode);
        if (offerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(offersById.get(offerId)).filter(this::isOfferActive);
    }

    public Optional<AuthorizationGrant> consumeAuthorizationCode(String code) {
        AuthorizationGrant grant = authorizationGrants.remove(code);
        if (grant == null || grant.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(grant);
    }

    public AccessToken issueAccessToken(CredentialOfferRecord offer) {
        String token = base64Url(randomBytes(32));
        String cNonce = base64Url(randomBytes(16));
        Instant now = Instant.now();
        AccessTokenGrant grant = new AccessTokenGrant(
                token,
                offer.offerId(),
                cNonce,
                now.plus(C_NONCE_TTL),
                now.plus(ACCESS_TOKEN_TTL)
        );
        accessTokens.put(token, grant);
        return new AccessToken(token, grant.cNonce(), grant.cNonceExpiresAt(), grant.expiresAt());
    }

    public Optional<AccessTokenGrant> resolveAccessToken(String token) {
        AccessTokenGrant grant = accessTokens.get(token);
        if (grant == null || grant.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(grant);
    }

    public Optional<CredentialOfferRecord> resolveOffer(String offerId) {
        return Optional.ofNullable(offersById.get(offerId)).filter(this::isOfferActive);
    }

    public CredentialIssuerMetadata buildCredentialIssuerMetadata() {
        String baseEndpoint = ensureIssuerEndpoint();
        CredentialIssuerMetadata metadata = new CredentialIssuerMetadata();
        metadata.setCredentialIssuer(resolveCredentialIssuerId());
        metadata.setCredentialEndpoint(baseEndpoint + "/oidc4vci/credential");
        metadata.setTokenEndpoint(baseEndpoint + "/oidc4vci/token");
        metadata.setAuthorizationServers(List.of(baseEndpoint));
        metadata.setGrantTypesSupported(List.of("authorization_code", "urn:ietf:params:oauth:grant-type:pre-authorized_code"));
        metadata.setDisplay(List.of(new DisplayEntry(resolveOrganizationName(), "en", "Issuance for Izylife public authority staff")));
        metadata.setCredentialConfigurationsSupported(Map.of(
                STAFF_CREDENTIAL_CONFIGURATION_ID,
                buildStaffCredentialConfiguration()
        ));
        return metadata;
    }

    public AuthorizationServerMetadata buildAuthorizationServerMetadata() {
        String baseEndpoint = ensureIssuerEndpoint();
        AuthorizationServerMetadata metadata = new AuthorizationServerMetadata();
        metadata.setIssuer(baseEndpoint);
        metadata.setAuthorizationEndpoint(baseEndpoint + "/oidc4vci/authorize");
        metadata.setTokenEndpoint(baseEndpoint + "/oidc4vci/token");
        metadata.setResponseTypesSupported(List.of("code"));
        metadata.setGrantTypesSupported(List.of("authorization_code", "urn:ietf:params:oauth:grant-type:pre-authorized_code"));
        metadata.setCodeChallengeMethodsSupported(List.of("S256"));
        return metadata;
    }

    public String buildStaffCredentialJwt(StaffProfile profile) {
        Map<String, Object> vc = buildStaffCredentialClaims(profile);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(365, ChronoUnit.DAYS);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer(resolveCredentialIssuerId())
                .subject(profile.subjectDid())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("vc", vc)
                .build();

        JWSHeader header = issuerSigningService.getHeaderTemplate();
        JWSSigner signer = issuerSigningService.getSigner();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to sign credential JWT", ex);
        }
        return jwt.serialize();
    }

    private CredentialConfiguration buildStaffCredentialConfiguration() {
        CredentialConfiguration configuration = new CredentialConfiguration();
        configuration.setFormat("jwt_vc_json");
        configuration.setScope("staff_credential");
        configuration.setCryptographicBindingMethodsSupported(List.of("did:web", "did:key"));
        configuration.setCredentialSigningAlgValuesSupported(List.of(issuerSigningService.getAlgorithm().getName()));
        configuration.setDisplay(List.of(new DisplayEntry("Public Authority Staff Credential", "en")));
        List<CredentialSubjectField> fields = new ArrayList<>();
        fields.add(new CredentialSubjectField(List.of("$.credentialSubject.familyName"), false, "Family name"));
        fields.add(new CredentialSubjectField(List.of("$.credentialSubject.givenName"), false, "Given name"));
        fields.add(new CredentialSubjectField(List.of("$.credentialSubject.role"), false, "Role"));
        fields.add(new CredentialSubjectField(List.of("$.credentialSubject.employeeNumber"), false, "Employee number"));
        fields.add(new CredentialSubjectField(List.of("$.credentialSubject.email"), true, "Work email"));
        CredentialDefinition definition = new CredentialDefinition(
                List.of("VerifiableCredential", "PublicAuthorityStaffCredential"),
                new CredentialSubjectMetadata(fields)
        );
        configuration.setCredentialDefinition(definition);
        ProofTypeMetadata proofMetadata = new ProofTypeMetadata();
        proofMetadata.setProofSigningAlgValuesSupported(List.of(issuerSigningService.getAlgorithm().getName()));
        configuration.setProofTypesSupported(Map.of("jwt", proofMetadata));
        return configuration;
    }

    private boolean isOfferActive(CredentialOfferRecord record) {
        return record.createdAt().plus(OFFER_TTL).isAfter(Instant.now());
    }

    private String resolveOrganizationName() {
        return Optional.ofNullable(appProperties.getIssuer().getOrganizationName()).orElse("Izylife Public Authority");
    }

    private String ensureIssuerEndpoint() {
        return Optional.ofNullable(appProperties.getIssuer().getEndpoint())
                .filter(value -> !value.isBlank())
                .orElse("http://localhost:9090");
    }

    private String resolveCredentialIssuerId() {
        String configured = appProperties.getIssuer().getCredentialIssuerId();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return ensureIssuerEndpoint();
    }

    private String randomIdentifier() {
        return base64Url(randomBytes(18));
    }

    private byte[] randomBytes(int size) {
        byte[] buffer = new byte[size];
        secureRandom.nextBytes(buffer);
        return buffer;
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Map<String, Object> buildStaffCredentialClaims(StaffProfile profile) {
        Map<String, Object> credential = new LinkedHashMap<>();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        credential.put("@context", List.of(
                "https://www.w3.org/2018/credentials/v1",
                "https://www.w3.org/ns/credentials/examples/v2",
                "https://schemas.izylife.example/credentials/public-authority-staff/v1"
        ));
        credential.put("id", "urn:uuid:" + UUID.randomUUID());
        credential.put("type", List.of("VerifiableCredential", "PublicAuthorityStaffCredential"));
        credential.put("issuer", Map.of(
                "id", resolveCredentialIssuerId(),
                "name", resolveOrganizationName()
        ));
        credential.put("issuanceDate", now.toString());
        credential.put("expirationDate", now.plus(365, ChronoUnit.DAYS).toString());
        credential.put("credentialSchema", List.of(Map.of(
                "id", "https://schemas.izylife.example/credentials/public-authority-staff/v1",
                "type", "JsonSchemaValidator2018"
        )));

        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("id", profile.subjectDid());
        subject.put("familyName", profile.familyName());
        subject.put("givenName", profile.givenName());
        subject.put("role", profile.role());
        subject.put("employeeNumber", profile.employeeNumber());
        if (profile.email() != null) {
            subject.put("email", profile.email());
        }
        credential.put("credentialSubject", subject);

        credential.put("credentialStatus", Map.of(
                "id", ensureIssuerEndpoint() + "/status/" + profile.employeeNumber(),
                "type", "StatusList2021Entry",
                "statusPurpose", "revocation",
                "statusListIndex", String.valueOf(Math.abs(profile.employeeNumber().hashCode()) % 2048),
                "statusListCredential", ensureIssuerEndpoint() + "/status-list/2024"
        ));
        return credential;
    }

    public record AuthorizationRequest(String clientId, String redirectUri) {
    }

    public record AuthorizationCode(String code, Instant expiresAt) {
    }

    public record AuthorizationGrant(String code, String offerId, String clientId, String redirectUri, Instant expiresAt) {
    }

    public record AccessToken(String value, String cNonce, Instant cNonceExpiresAt, Instant expiresAt) {
    }

    public record AccessTokenGrant(String value, String offerId, String cNonce, Instant cNonceExpiresAt, Instant expiresAt) {
    }

    public record CredentialOfferRecord(String offerId,
                                         String issuerState,
                                         String preAuthorizedCode,
                                         boolean userPinRequired,
                                         List<String> credentialConfigurationIds,
                                         StaffProfile profile,
                                         Instant createdAt) {
    }

    public record StaffProfile(String subjectDid,
                               String familyName,
                               String givenName,
                               String role,
                               String employeeNumber,
                               String email) {
    }
}
