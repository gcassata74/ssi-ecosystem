package com.izylife.ssi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.config.AppProperties.SigningKeyProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class Oidc4VpRequestService {

    private static final Duration DEFAULT_REQUEST_TTL = Duration.ofMinutes(5);

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final JWSSigner signer;
    private final JWSAlgorithm signingAlgorithm;
    private final JWKSet publicJwkSet;
    private final JsonNode presentationDefinition;
    private final Cache<String, AuthorizationSession> sessions = Caffeine.newBuilder()
            .expireAfterWrite(DEFAULT_REQUEST_TTL)
            .build();
    private final Set<String> inputDescriptorIds;

    public Oidc4VpRequestService(AppProperties appProperties, ObjectMapper objectMapper) throws IOException, JOSEException {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;

        SigningKeyProperties signingKeyProperties = appProperties.getVerifier().getSigningKey();
        ECKey signerKey = buildSigningKey(signingKeyProperties);
        this.signer = new ECDSASigner(signerKey);
        this.signingAlgorithm = signerKey.getAlgorithm() != null
                ? JWSAlgorithm.parse(signerKey.getAlgorithm().getName())
                : JWSAlgorithm.ES256;
        this.publicJwkSet = new JWKSet(signerKey.toPublicJWK());
        this.presentationDefinition = loadPresentationDefinition(appProperties.getVerifier().getPresentationDefinitionId());
        this.inputDescriptorIds = Collections.unmodifiableSet(extractInputDescriptorIds(this.presentationDefinition));
    }

    public AuthorizationRequest createAuthorizationRequest() {
        String state = UUID.randomUUID().toString();
        String nonce = "nonce-" + state.substring(0, 8);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(DEFAULT_REQUEST_TTL);

        String verifierEndpoint = normalizeEndpoint(appProperties.getVerifier().getEndpoint());
        String clientId = defaultString(appProperties.getVerifier().getClientId(), verifierEndpoint + "/oidc4vp/responses");
        String responseMode = defaultString(appProperties.getVerifier().getResponseMode(), "direct_post");
        String clientIdScheme = defaultString(appProperties.getVerifier().getClientIdScheme(), "redirect_uri");
        String requestId = state;
        String requestUri = verifierEndpoint + "/oidc4vp/requests/" + requestId;
        String presentationDefinitionId = appProperties.getVerifier().getPresentationDefinitionId();
        String presentationDefinitionUri = verifierEndpoint + "/definitions/" + presentationDefinitionId + ".json";

        String requestObject = buildSignedRequestObject(
                state,
                nonce,
                clientId,
                clientIdScheme,
                responseMode,
                presentationDefinitionUri,
                now,
                expiresAt
        );

        sessions.put(state, new AuthorizationSession(state, nonce, requestObject, expiresAt));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", clientId);
        params.put("client_id_scheme", clientIdScheme);
        params.put("request_uri", requestUri);
        params.put("response_type", "vp_token");
        params.put("response_mode", responseMode);
        params.put("scope", "openid");
        params.put("nonce", nonce);
        params.put("state", state);
        params.put("presentation_definition_uri", presentationDefinitionUri);

        String qrPayload = "openid://?" + buildQuery(params);

        return new AuthorizationRequest(state, nonce, requestUri, requestObject, qrPayload, presentationDefinitionId, presentationDefinitionUri, clientId);
    }

    public Optional<String> getRequestObject(String requestId) {
        AuthorizationSession session = sessions.getIfPresent(requestId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired()) {
            sessions.invalidate(requestId);
            return Optional.empty();
        }
        return Optional.of(session.requestObject());
    }

    public Optional<AuthorizationSession> resolveSession(String state) {
        AuthorizationSession session = sessions.getIfPresent(state);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired()) {
            sessions.invalidate(state);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void consumeSession(String state) {
        sessions.invalidate(state);
    }

    public JsonNode getPresentationDefinition() {
        return presentationDefinition;
    }

    public Set<String> getInputDescriptorIds() {
        return inputDescriptorIds;
    }

    public JWKSet getPublicJwkSet() {
        return publicJwkSet;
    }

    private String buildSignedRequestObject(
            String state,
            String nonce,
            String clientId,
            String clientIdScheme,
            String responseMode,
            String presentationDefinitionUri,
            Instant issuedAt,
            Instant expiresAt
    ) {
        try {
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .issuer(clientId)
                    .audience(defaultString(appProperties.getVerifier().getRequestAudience(), "https://self-issued.me/v2"))
                    .subject(state)
                    .expirationTime(Date.from(expiresAt))
                    .issueTime(Date.from(issuedAt))
                    .claim("response_type", "vp_token")
                    .claim("scope", "openid")
                    .claim("client_id", clientId)
                    .claim("client_id_scheme", clientIdScheme)
                    .claim("response_mode", responseMode)
                    .claim("response_uri", clientId)
                    .claim("nonce", nonce)
                    .claim("state", state)
                    .claim("presentation_definition", objectMapper.convertValue(presentationDefinition, Map.class))
                    .claim("presentation_definition_uri", presentationDefinitionUri)
                    .claim("client_metadata", buildClientMetadata());

            SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(signingAlgorithm)
                    .keyID(publicJwkSet.getKeys().get(0).getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build(), claimsBuilder.build());

            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign OIDC4VP request object", e);
        }
    }

    private Map<String, Object> buildClientMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        JsonNode proofTypeNode = presentationDefinition.path("format").path("ldp_vp").path("proof_type");
        Object proofTypes = proofTypeNode.isMissingNode()
                ? null
                : objectMapper.convertValue(proofTypeNode, Object.class);
        if (proofTypes != null) {
            metadata.put("vp_formats", Map.of("ldp_vp", Map.of("proof_type", proofTypes)));
        }
        metadata.put("jwks", publicJwkSet.toJSONObject());
        metadata.put("id_token_signed_response_alg", signingAlgorithm.getName());
        metadata.put("token_endpoint_auth_method", "none");
        return metadata;
    }

    private JsonNode loadPresentationDefinition(String definitionId) throws IOException {
        String path = "definitions/" + definitionId + ".json";
        Resource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IOException("Presentation definition resource not found: " + path);
        }
        return objectMapper.readTree(resource.getInputStream());
    }

    private Set<String> extractInputDescriptorIds(JsonNode definition) {
        Set<String> ids = new LinkedHashSet<>();
        if (definition == null) {
            return ids;
        }
        JsonNode descriptors = definition.get("input_descriptors");
        if (descriptors != null && descriptors.isArray()) {
            for (JsonNode descriptor : descriptors) {
                if (descriptor == null) {
                    continue;
                }
                JsonNode idNode = descriptor.get("id");
                if (idNode != null) {
                    String id = idNode.asText(null);
                    if (id != null && !id.isBlank()) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    private static ECKey buildSigningKey(SigningKeyProperties properties) throws JOSEException {
        if (properties == null || properties.getKty() == null || !properties.getKty().equalsIgnoreCase("EC")) {
            throw new JOSEException("Only EC signing keys are supported for OIDC4VP request objects");
        }

        Curve curve = Curve.parse(properties.getCrv());

        return new ECKey.Builder(
                curve,
                new Base64URL(properties.getX()),
                new Base64URL(properties.getY())
        )
                .d(new Base64URL(properties.getD()))
                .keyID(properties.getKid())
                .algorithm(new JWSAlgorithm(properties.getAlg()))
                .build();
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private String defaultString(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String buildQuery(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            builder.append(entry.getKey())
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return builder.toString();
    }

    public record AuthorizationRequest(
            String state,
            String nonce,
            String requestUri,
            String requestObject,
            String qrPayload,
            String presentationDefinitionId,
            String presentationDefinitionUri,
            String clientId
    ) {
    }

    public record AuthorizationSession(String state, String nonce, String requestObject, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
