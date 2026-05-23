/*
 * SSI Issuer
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.keycloak.oidc-enabled", havingValue = "true")
public class KeycloakRealmConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakRealmConfigService.class);

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public KeycloakRealmConfigService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    public RealmDidInfo generateDid(String realm) {
        String url = resolveBaseUrl() + "/realms/" + realm + "/ssi-issuer/did";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(fetchAdminToken());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(headers), Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Empty response from Keycloak SPI generate DID endpoint");
        }
        String did = (String) body.get("did");
        @SuppressWarnings("unchecked")
        Map<String, Object> publicJwk = (Map<String, Object>) body.get("publicJwk");
        return new RealmDidInfo(did, publicJwk);
    }

    public RealmDidInfo getRealmDid(String realm) {
        String url = resolveBaseUrl() + "/realms/" + realm + "/ssi-issuer/did";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(fetchAdminToken());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new IllegalStateException("Empty response from Keycloak SPI DID endpoint");
        }
        String did = (String) body.get("did");
        @SuppressWarnings("unchecked")
        Map<String, Object> publicJwk = (Map<String, Object>) body.get("publicJwk");
        return new RealmDidInfo(did, publicJwk);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRealmPrivateJwk(String realm) {
        String url = resolveBaseUrl() + "/realms/" + realm + "/ssi-issuer/did/private-jwk";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(fetchAdminToken());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        if (response.getBody() == null) {
            throw new IllegalStateException("Empty response from Keycloak SPI private-jwk endpoint");
        }
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    public List<ClaimMapping> getClaimConfig(String realm) {
        String url = resolveBaseUrl() + "/realms/" + realm + "/ssi-issuer/credential-config";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(fetchAdminToken());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        if (response.getBody() == null) {
            return List.of();
        }
        Object claimsRaw = response.getBody().get("claims");
        try {
            String json = objectMapper.writeValueAsString(claimsRaw);
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            LOGGER.warn("Failed to deserialize claim config for realm {}", realm, e);
            return List.of();
        }
    }

    public void updateClaimConfig(String realm, List<ClaimMapping> claims) {
        String url = resolveBaseUrl() + "/realms/" + realm + "/ssi-issuer/credential-config";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(fetchAdminToken());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        try {
            String body = objectMapper.writeValueAsString(claims);
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update claim config for realm " + realm, e);
        }
    }

    private String fetchAdminToken() {
        AppProperties.KeycloakProperties kc = appProperties.getKeycloak();
        String tokenUrl = resolveBaseUrl() + "/realms/master/protocol/openid-connect/token";

        String username = kc.getAdminUsername();
        String password = kc.getAdminPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Keycloak admin credentials not configured (app.keycloak.admin-username / admin-password)");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String body = "grant_type=password"
                + "&client_id=admin-cli"
                + "&username=" + username
                + "&password=" + password;
        ResponseEntity<Map> response = restTemplate.exchange(tokenUrl, HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        if (response.getBody() == null) {
            throw new IllegalStateException("Failed to obtain Keycloak admin token");
        }
        return (String) response.getBody().get("access_token");
    }

    private String resolveBaseUrl() {
        String base = appProperties.getKeycloak().getBaseUrl();
        if (base == null || base.isBlank()) {
            return "http://localhost:8180";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public record RealmDidInfo(String did, Map<String, Object> publicJwk) {}

    public record ClaimMapping(String keycloakClaim, String credentialClaim, boolean mandatory) {}
}
