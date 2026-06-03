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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.keycloak.oidc-enabled", havingValue = "true")
public class KeycloakRealmConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakRealmConfigService.class);

    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String ATTR_CLAIMS = "ssi.credential.claims";
    private static final List<ClaimMapping> DEFAULT_CLAIMS = List.of(
            new ClaimMapping("given_name", "givenName", true),
            new ClaimMapping("family_name", "familyName", true),
            new ClaimMapping("email", "email", false),
            new ClaimMapping("codice_fiscale", "codiceFiscale", true),
            new ClaimMapping("indirizzo", "indirizzo", true),
            new ClaimMapping("employee_id", "employeeNumber", false),
            new ClaimMapping("job_title", "role", false)
    );

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public KeycloakRealmConfigService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    public RealmDidInfo getRealmDid(String realm) {
        Map<String, Object> attrs = fetchOrInitRealmDid(realm);
        String did = (String) attrs.get("ssi.did");
        String publicJwkJson = (String) attrs.get("ssi.did.public-jwk");
        Map<String, Object> publicJwk = null;
        if (publicJwkJson != null) {
            try {
                publicJwk = objectMapper.readValue(publicJwkJson, new TypeReference<>() {});
            } catch (Exception e) {
                LOGGER.warn("Failed to parse public JWK for realm {}", realm, e);
            }
        }
        return new RealmDidInfo(did, publicJwk);
    }

    public Map<String, Object> getRealmPrivateJwk(String realm) {
        Map<String, Object> attrs = fetchOrInitRealmDid(realm);
        String privateJwkJson = (String) attrs.get("ssi.did.private-jwk");
        if (privateJwkJson == null) {
            throw new IllegalStateException("No private JWK found for realm " + realm);
        }
        try {
            return objectMapper.readValue(privateJwkJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse private JWK for realm " + realm, e);
        }
    }

    public List<ClaimMapping> getClaimMappings(String realm) {
        Map<String, Object> attrs = fetchRealmAttributes(realm);
        String mappingsJson = (String) attrs.get(ATTR_CLAIMS);
        if (mappingsJson == null || mappingsJson.isBlank()) {
            return DEFAULT_CLAIMS;
        }
        try {
            List<ClaimMapping> mappings = objectMapper.readValue(mappingsJson, new TypeReference<>() {});
            return (mappings == null || mappings.isEmpty()) ? DEFAULT_CLAIMS : mappings;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse claim mappings for realm {}, using defaults", realm, e);
            return DEFAULT_CLAIMS;
        }
    }

    /**
     * Returns realm attributes, auto-generating and persisting the DID:key pair
     * via the Keycloak Admin REST API if it doesn't exist yet.
     * The SPI postInit() runs before realm import so the first enrollment triggers this.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchOrInitRealmDid(String realm) {
        Map<String, Object> attrs = fetchRealmAttributes(realm);
        if (attrs.containsKey("ssi.did")) {
            return attrs;
        }
        LOGGER.info("No DID:key found for realm '{}', auto-generating via Admin REST API", realm);
        Map<String, String> didAttrs = generateDidAttributes();
        storeRealmAttributes(realm, didAttrs);
        Map<String, Object> updated = new HashMap<>(attrs);
        updated.putAll(didAttrs);
        return updated;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchRealmAttributes(String realm) {
        String url = resolveBaseUrl() + "/admin/realms/" + realm;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(fetchAdminToken());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        if (response.getBody() == null) {
            throw new IllegalStateException("Empty response from Keycloak Admin API for realm " + realm);
        }
        Object attrsRaw = response.getBody().get("attributes");
        if (attrsRaw instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private void storeRealmAttributes(String realm, Map<String, String> newAttrs) {
        // GET full realm representation, merge attributes, PUT back
        String url = resolveBaseUrl() + "/admin/realms/" + realm;
        String token = fetchAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> getResponse = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        if (getResponse.getBody() == null) {
            throw new IllegalStateException("Cannot read realm " + realm + " for attribute update");
        }
        Map<String, Object> realmRep = new HashMap<>(getResponse.getBody());
        Object existingAttrsRaw = realmRep.get("attributes");
        Map<String, Object> mergedAttrs = new HashMap<>();
        if (existingAttrsRaw instanceof Map<?, ?> m) {
            mergedAttrs.putAll((Map<String, Object>) m);
        }
        mergedAttrs.putAll(newAttrs);
        realmRep.put("attributes", mergedAttrs);

        HttpHeaders putHeaders = new HttpHeaders();
        putHeaders.setBearerAuth(token);
        putHeaders.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.exchange(url, HttpMethod.PUT,
                    new HttpEntity<>(objectMapper.writeValueAsString(realmRep), putHeaders), Void.class);
            LOGGER.info("Stored DID:key attributes for realm '{}'", realm);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store DID attributes for realm " + realm, e);
        }
    }

    private Map<String, String> generateDidAttributes() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = kpg.generateKeyPair();
            ECPublicKey pub = (ECPublicKey) kp.getPublic();
            ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();

            byte[] x32 = to32Bytes(pub.getW().getAffineX());
            byte[] y32 = to32Bytes(pub.getW().getAffineY());
            byte[] d32 = to32Bytes(priv.getS());

            // Compressed public key: 0x02/0x03 + x
            byte[] compressed = new byte[33];
            compressed[0] = pub.getW().getAffineY().testBit(0) ? (byte) 0x03 : (byte) 0x02;
            System.arraycopy(x32, 0, compressed, 1, 32);

            // DID:key — multicodec P-256 prefix [0x80, 0x24] + compressed key
            byte[] multicodec = new byte[35];
            multicodec[0] = (byte) 0x80;
            multicodec[1] = (byte) 0x24;
            System.arraycopy(compressed, 0, multicodec, 2, 33);
            String did = "did:key:z" + base58Encode(multicodec);

            String b64x = Base64.getUrlEncoder().withoutPadding().encodeToString(x32);
            String b64y = Base64.getUrlEncoder().withoutPadding().encodeToString(y32);
            String b64d = Base64.getUrlEncoder().withoutPadding().encodeToString(d32);

            Map<String, Object> publicJwk = new LinkedHashMap<>();
            publicJwk.put("kty", "EC");
            publicJwk.put("crv", "P-256");
            publicJwk.put("x", b64x);
            publicJwk.put("y", b64y);
            publicJwk.put("kid", did);

            Map<String, Object> privateJwk = new LinkedHashMap<>(publicJwk);
            privateJwk.put("d", b64d);

            return Map.of(
                    "ssi.did", did,
                    "ssi.did.public-jwk", objectMapper.writeValueAsString(publicJwk),
                    "ssi.did.private-jwk", objectMapper.writeValueAsString(privateJwk)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate DID:key pair", e);
        }
    }

    private static byte[] to32Bytes(BigInteger n) {
        byte[] raw = n.toByteArray();
        if (raw.length == 32) return raw;
        if (raw.length == 33 && raw[0] == 0) return Arrays.copyOfRange(raw, 1, 33);
        byte[] out = new byte[32];
        int srcOff = Math.max(0, raw.length - 32);
        int dstOff = Math.max(0, 32 - raw.length);
        System.arraycopy(raw, srcOff, out, dstOff, Math.min(raw.length, 32));
        return out;
    }

    private static String base58Encode(byte[] input) {
        BigInteger n = new BigInteger(1, input);
        BigInteger base = BigInteger.valueOf(58);
        StringBuilder sb = new StringBuilder();
        while (n.signum() > 0) {
            BigInteger[] divRem = n.divideAndRemainder(base);
            sb.append(BASE58_ALPHABET.charAt(divRem[1].intValue()));
            n = divRem[0];
        }
        for (byte b : input) {
            if (b != 0) break;
            sb.append(BASE58_ALPHABET.charAt(0));
        }
        return sb.reverse().toString();
    }

    @SuppressWarnings("unchecked")
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
