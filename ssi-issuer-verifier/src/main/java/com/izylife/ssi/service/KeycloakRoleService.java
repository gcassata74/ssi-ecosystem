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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.config.AppProperties.KeycloakProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KeycloakRoleService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakRoleService.class);
    private static final String TOKEN_CACHE_KEY = "keycloak-token";
    private static final Duration TOKEN_SAFETY_WINDOW = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final KeycloakProperties properties;
    private final Cache<String, CachedToken> tokenCache;
    private final String normalizedBaseUrl;

    public KeycloakRoleService(AppProperties appProperties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = appProperties.getKeycloak();
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.tokenCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
        this.normalizedBaseUrl = Optional.ofNullable(properties.getBaseUrl())
                .map(url -> url.endsWith("/") ? url.substring(0, url.length() - 1) : url)
                .orElse(null);
    }

    public List<String> resolveRolesForHolder(String holderDid) {
        if (!isEnabled() || !StringUtils.hasText(holderDid)) {
            return Collections.emptyList();
        }

        try {
            String accessToken = accessToken();
            if (accessToken == null) {
                log.debug("Keycloak access token not available; skipping role lookup");
                return Collections.emptyList();
            }

            Optional<KeycloakUserRepresentation> userRepresentation = lookupUser(holderDid, accessToken);
            if (userRepresentation.isEmpty()) {
                log.debug("No Keycloak user found for DID '{}'", holderDid);
                return Collections.emptyList();
            }

            KeycloakUserRepresentation user = userRepresentation.get();
            Set<String> roles = new HashSet<>(fetchRealmRoles(user.id(), accessToken));
            if (properties.isIncludeClientRoles()) {
                roles.addAll(fetchClientRoles(user.id(), accessToken));
            }
            return roles.stream()
                    .sorted()
                    .toList();
        } catch (RestClientException ex) {
            log.warn("Failed to resolve Keycloak roles for DID {}", holderDid, ex);
            return Collections.emptyList();
        }
    }

    public String rolesClaimName() {
        if (properties == null) {
            return "roles";
        }
        return StringUtils.hasText(properties.getRolesClaim()) ? properties.getRolesClaim() : "roles";
    }

    private boolean isEnabled() {
        return properties != null
                && properties.isEnabled()
                && StringUtils.hasText(properties.getRealm())
                && StringUtils.hasText(normalizedBaseUrl)
                && StringUtils.hasText(properties.getClientId())
                && StringUtils.hasText(properties.getClientSecret());
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

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        ResponseEntity<KeycloakTokenResponse> response = restTemplate.postForEntity(tokenEndpoint(), entity, KeycloakTokenResponse.class);
        KeycloakTokenResponse body = response.getBody();
        if (body == null || !StringUtils.hasText(body.getAccessToken())) {
            log.warn("Keycloak token endpoint returned an empty response");
            return null;
        }

        long expiresIn = body.getExpiresIn() != null ? body.getExpiresIn() : 60L;
        Instant expiresAt = Instant.now().plusSeconds(Math.max(10, expiresIn)).minus(TOKEN_SAFETY_WINDOW);
        if (expiresAt.isBefore(Instant.now())) {
            expiresAt = Instant.now().plus(Duration.ofMinutes(1));
        }

        CachedToken token = new CachedToken(body.getAccessToken(), expiresAt);
        tokenCache.put(TOKEN_CACHE_KEY, token);
        return token.value();
    }

    private Optional<KeycloakUserRepresentation> lookupUser(String holderDid, String accessToken) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl(adminEndpoint("/users"))
                .queryParam("exact", true);

        String lookupAttribute = properties.getUserLookupAttribute();
        if (!StringUtils.hasText(lookupAttribute) || "username".equalsIgnoreCase(lookupAttribute)) {
            uriBuilder.queryParam("username", holderDid);
        } else {
            uriBuilder.queryParam("q", "attributes." + lookupAttribute + ":" + holderDid);
        }

        ResponseEntity<List<KeycloakUserRepresentation>> response = restTemplate.exchange(
                uriBuilder.build(true).toUri(),
                HttpMethod.GET,
                new HttpEntity<>(authorizationHeaders(accessToken)),
                new ParameterizedTypeReference<List<KeycloakUserRepresentation>>() {}
        );
        List<KeycloakUserRepresentation> users = response.getBody();
        if (users == null || users.isEmpty()) {
            return Optional.empty();
        }

        if (!StringUtils.hasText(lookupAttribute) || "username".equalsIgnoreCase(lookupAttribute)) {
            return Optional.of(users.get(0));
        }

        return users.stream()
                .filter(user -> hasMatchingAttribute(user, lookupAttribute, holderDid))
                .findFirst();
    }

    private boolean hasMatchingAttribute(KeycloakUserRepresentation user, String attributeName, String value) {
        Map<String, List<String>> attributes = user.attributes();
        if (attributes == null || attributes.isEmpty()) {
            return false;
        }
        List<String> values = attributes.get(attributeName);
        if (values == null) {
            return false;
        }
        return values.stream().filter(Objects::nonNull).anyMatch(v -> v.equalsIgnoreCase(value));
    }

    private List<String> fetchRealmRoles(String userId, String accessToken) {
        ResponseEntity<List<KeycloakRoleRepresentation>> response = restTemplate.exchange(
                adminEndpoint("/users/" + userId + "/role-mappings/realm"),
                HttpMethod.GET,
                new HttpEntity<>(authorizationHeaders(accessToken)),
                new ParameterizedTypeReference<List<KeycloakRoleRepresentation>>() {}
        );
        List<KeycloakRoleRepresentation> body = response.getBody();
        if (body == null || body.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> roles = new ArrayList<>(body.size());
        for (KeycloakRoleRepresentation roleRepresentation : body) {
            if (roleRepresentation != null && StringUtils.hasText(roleRepresentation.getName())) {
                roles.add(roleRepresentation.getName());
            }
        }
        return roles;
    }

    private Set<String> fetchClientRoles(String userId, String accessToken) {
        ResponseEntity<KeycloakRoleMappingResponse> response = restTemplate.exchange(
                adminEndpoint("/users/" + userId + "/role-mappings"),
                HttpMethod.GET,
                new HttpEntity<>(authorizationHeaders(accessToken)),
                KeycloakRoleMappingResponse.class
        );

        KeycloakRoleMappingResponse body = response.getBody();
        if (body == null || body.getClientMappings() == null || body.getClientMappings().isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> allowedClients = null;
        if (properties.getClientRoleClients() != null && !properties.getClientRoleClients().isEmpty()) {
            allowedClients = properties.getClientRoleClients().stream()
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
        }

        Set<String> roles = new HashSet<>();
        for (Map.Entry<String, KeycloakClientRoleContainer> entry : body.getClientMappings().entrySet()) {
            KeycloakClientRoleContainer container = entry.getValue();
            if (container == null || container.getMappings() == null || container.getMappings().isEmpty()) {
                continue;
            }
            String clientKey = entry.getKey();
            String clientId = StringUtils.hasText(container.getClient()) ? container.getClient() : clientKey;
            if (allowedClients != null && !allowedClients.contains(clientId) && !allowedClients.contains(container.getId())) {
                continue;
            }
            for (KeycloakRoleRepresentation mapping : container.getMappings()) {
                if (mapping != null && StringUtils.hasText(mapping.getName())) {
                    roles.add(clientId + ":" + mapping.getName());
                }
            }
        }
        return roles;
    }

    private HttpHeaders authorizationHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private record CachedToken(String value, Instant expiresAt) {
    }

    private static class KeycloakTokenResponse {
        private String access_token;
        private Long expires_in;

        public String getAccessToken() {
            return access_token;
        }

        public Long getExpiresIn() {
            return expires_in;
        }

        public void setAccess_token(String access_token) {
            this.access_token = access_token;
        }

        public void setExpires_in(Long expires_in) {
            this.expires_in = expires_in;
        }
    }

    private record KeycloakUserRepresentation(String id,
                                              String username,
                                              Map<String, List<String>> attributes) {
    }

    private static class KeycloakRoleRepresentation {
        private String id;
        private String name;
        private String clientId;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }

    private static class KeycloakRoleMappingResponse {
        private List<KeycloakRoleRepresentation> realmMappings;
        private Map<String, KeycloakClientRoleContainer> clientMappings;

        public List<KeycloakRoleRepresentation> getRealmMappings() {
            return realmMappings;
        }

        public void setRealmMappings(List<KeycloakRoleRepresentation> realmMappings) {
            this.realmMappings = realmMappings;
        }

        public Map<String, KeycloakClientRoleContainer> getClientMappings() {
            return clientMappings;
        }

        public void setClientMappings(Map<String, KeycloakClientRoleContainer> clientMappings) {
            this.clientMappings = clientMappings;
        }
    }

    private static class KeycloakClientRoleContainer {
        private String id;
        private String client;
        private List<KeycloakRoleRepresentation> mappings;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getClient() {
            return client;
        }

        public void setClient(String client) {
            this.client = client;
        }

        public List<KeycloakRoleRepresentation> getMappings() {
            return mappings;
        }

        public void setMappings(List<KeycloakRoleRepresentation> mappings) {
            this.mappings = mappings;
        }
    }
}
