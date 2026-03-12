package com.izylife.ssi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.config.AppProperties.KeycloakProperties;
import com.izylife.ssi.config.AppProperties.KeycloakProperties.PresentationDefinitionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class KeycloakPresentationDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakPresentationDefinitionService.class);
    private static final String TOKEN_CACHE_KEY = "keycloak-token";
    private static final String CACHE_KEY = "presentation-definition";
    private static final Duration TOKEN_SAFETY_WINDOW = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final KeycloakProperties properties;
    private final PresentationDefinitionProperties pdProperties;
    private final Cache<String, CachedToken> tokenCache;
    private final Cache<String, JsonNode> definitionCache;
    private final String normalizedBaseUrl;

    public KeycloakPresentationDefinitionService(AppProperties appProperties,
                                                 RestTemplateBuilder restTemplateBuilder,
                                                 ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.properties = appProperties.getKeycloak();
        this.pdProperties = properties != null ? properties.getPresentationDefinition() : null;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.tokenCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();

        Duration cacheTtl = pdProperties != null && pdProperties.getCacheTtl() != null
                ? pdProperties.getCacheTtl()
                : Duration.ofSeconds(30);
        if (cacheTtl.isZero() || cacheTtl.isNegative()) {
            cacheTtl = Duration.ofSeconds(30);
        }
        this.definitionCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .build();
        this.normalizedBaseUrl = properties != null && StringUtils.hasText(properties.getBaseUrl())
                ? normalizeBaseUrl(properties.getBaseUrl())
                : null;
    }

    public Optional<JsonNode> getPresentationDefinition(String expectedDefinitionId) {
        if (!isEnabled()) {
            return Optional.empty();
        }

        JsonNode cached = definitionCache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            return Optional.of(cached.deepCopy());
        }

        try {
            String accessToken = accessToken();
            if (accessToken == null) {
                return Optional.empty();
            }

            Optional<String> json = fetchPresentationDefinitionJson(accessToken);
            if (json.isEmpty()) {
                return Optional.empty();
            }

            JsonNode definition = parseDefinition(json.get(), expectedDefinitionId);
            definitionCache.put(CACHE_KEY, definition);
            return Optional.of(definition.deepCopy());
        } catch (IOException | RestClientException ex) {
            log.warn("Failed to load presentation definition from Keycloak", ex);
            return Optional.empty();
        }
    }

    public Optional<byte[]> getPresentationDefinitionBytes(String expectedDefinitionId) {
        return getPresentationDefinition(expectedDefinitionId)
                .map(jsonNode -> jsonNode.toString().getBytes(StandardCharsets.UTF_8));
    }

    private boolean isEnabled() {
        return properties != null
                && properties.isEnabled()
                && pdProperties != null
                && pdProperties.isEnabled()
                && StringUtils.hasText(normalizedBaseUrl)
                && StringUtils.hasText(properties.getRealm())
                && StringUtils.hasText(properties.getClientId())
                && StringUtils.hasText(properties.getClientSecret())
                && StringUtils.hasText(pdProperties.getClientId())
                && StringUtils.hasText(pdProperties.getAttribute());
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String adminEndpoint(String path) {
        if (!StringUtils.hasText(path)) {
            path = "";
        }
        return normalizedBaseUrl + "/admin/realms/" + properties.getRealm() + path;
    }

    private String tokenEndpoint() {
        return normalizedBaseUrl + "/realms/" + properties.getRealm() + "/protocol/openid-connect/token";
    }

    private String accessToken() {
        CachedToken cachedToken = tokenCache.getIfPresent(TOKEN_CACHE_KEY);
        if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now().plus(TOKEN_SAFETY_WINDOW))) {
            return cachedToken.value();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        org.springframework.util.MultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        ResponseEntity<KeycloakTokenResponse> response = restTemplate.postForEntity(
                tokenEndpoint(),
                new HttpEntity<>(form, headers),
                KeycloakTokenResponse.class
        );

        KeycloakTokenResponse body = response.getBody();
        if (body == null || !StringUtils.hasText(body.getAccessToken())) {
            log.warn("Keycloak token endpoint returned an empty response for presentation definition loading");
            return null;
        }

        long expiresIn = body.getExpiresIn() != null ? body.getExpiresIn() : 60L;
        Instant expiresAt = Instant.now()
                .plusSeconds(Math.max(10, expiresIn))
                .minus(TOKEN_SAFETY_WINDOW);
        if (expiresAt.isBefore(Instant.now())) {
            expiresAt = Instant.now().plus(Duration.ofMinutes(1));
        }

        CachedToken token = new CachedToken(body.getAccessToken(), expiresAt);
        tokenCache.put(TOKEN_CACHE_KEY, token);
        return token.value();
    }

    private Optional<String> fetchPresentationDefinitionJson(String accessToken) {
        Optional<String> internalClientId = resolveInternalClientId(pdProperties.getClientId(), accessToken);
        if (internalClientId.isEmpty()) {
            return Optional.empty();
        }

        ResponseEntity<KeycloakClientRepresentation> response = restTemplate.exchange(
                adminEndpoint("/clients/" + internalClientId.get()),
                HttpMethod.GET,
                new HttpEntity<>(authorizationHeaders(accessToken)),
                KeycloakClientRepresentation.class
        );

        KeycloakClientRepresentation body = response.getBody();
        if (body == null || body.getAttributes() == null) {
            log.warn("Keycloak client '{}' does not expose attributes", pdProperties.getClientId());
            return Optional.empty();
        }

        return extractAttribute(body.getAttributes(), pdProperties.getAttribute());
    }

    private Optional<String> resolveInternalClientId(String clientId, String accessToken) {
        HttpEntity<Void> entity = new HttpEntity<>(authorizationHeaders(accessToken));

        ResponseEntity<List<KeycloakClientSummary>> response = restTemplate.exchange(
                org.springframework.web.util.UriComponentsBuilder
                        .fromHttpUrl(adminEndpoint("/clients"))
                        .queryParam("clientId", clientId)
                        .build(true)
                        .toUri(),
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<KeycloakClientSummary>>() {}
        );

        List<KeycloakClientSummary> clients = response.getBody();
        if (clients == null || clients.isEmpty()) {
            log.warn("Keycloak client '{}' not found", clientId);
            return Optional.empty();
        }

        return Optional.ofNullable(clients.get(0).id());
    }

    private HttpHeaders authorizationHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    private Optional<String> extractAttribute(Map<String, ?> attributes, String attributeKey) {
        if (attributes == null || attributes.isEmpty()) {
            return Optional.empty();
        }
        Object raw = attributes.get(attributeKey);
        if (raw == null) {
            log.warn("Keycloak client attribute '{}' is missing", attributeKey);
            return Optional.empty();
        }
        if (raw instanceof String str && StringUtils.hasText(str)) {
            return Optional.of(str);
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof String str && StringUtils.hasText(str)) {
                return Optional.of(str);
            }
            if (first != null) {
                return Optional.of(first.toString());
            }
        }
        return Optional.of(raw.toString());
    }

    private JsonNode parseDefinition(String json, String expectedDefinitionId) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        if (StringUtils.hasText(expectedDefinitionId)) {
            String actualId = root.path("id").asText(null);
            if (actualId != null && !expectedDefinitionId.equals(actualId)) {
                log.warn("Presentation definition id '{}' loaded from Keycloak does not match expected id '{}'", actualId, expectedDefinitionId);
            }
        }
        return root;
    }

    private record CachedToken(String value, Instant expiresAt) {}

    private record KeycloakTokenResponse(String access_token, Long expires_in) {
        String getAccessToken() {
            return access_token;
        }

        Long getExpiresIn() {
            return expires_in;
        }
    }

    private record KeycloakClientSummary(String id, String clientId) {}

    private static class KeycloakClientRepresentation {
        private Map<String, Object> attributes;

        public Map<String, Object> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, Object> attributes) {
            this.attributes = attributes;
        }
    }
}
