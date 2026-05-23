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

package com.izylife.ssi.controller;

import com.izylife.ssi.dto.OnboardingQrResponse;
import com.izylife.ssi.service.KeycloakRealmConfigService;
import com.izylife.ssi.service.OnboardingStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(path = "/api/onboarding/issuer", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(name = "app.keycloak.oidc-enabled", havingValue = "true")
public class IssuerEnrollController {

    private static final Logger LOGGER = LoggerFactory.getLogger(IssuerEnrollController.class);

    // Standard OIDC/JWT claims that should not appear in the credential subject
    private static final Set<String> SKIP_CLAIMS = Set.of(
            "iss", "sub", "aud", "exp", "iat", "jti", "nbf", "nonce",
            "at_hash", "c_hash", "acr", "azp", "amr", "auth_time", "sid",
            "typ", "session_state", "realm_access", "resource_access",
            "scope", "allowed-origins", "email_verified", "preferred_username"
    );

    private final KeycloakRealmConfigService realmConfigService;
    private final OnboardingStateService onboardingStateService;

    public IssuerEnrollController(KeycloakRealmConfigService realmConfigService,
                                   OnboardingStateService onboardingStateService) {
        this.realmConfigService = realmConfigService;
        this.onboardingStateService = onboardingStateService;
    }

    @PostMapping("/enroll")
    public ResponseEntity<OnboardingQrResponse> enroll(@AuthenticationPrincipal Jwt jwt) {
        String iss = jwt.getIssuer().toString();
        String realm = iss.substring(iss.lastIndexOf('/') + 1);

        try {
            KeycloakRealmConfigService.RealmDidInfo didInfo = realmConfigService.getRealmDid(realm);
            Map<String, Object> privateJwk = realmConfigService.getRealmPrivateJwk(realm);

            Map<String, Object> credentialSubject = new LinkedHashMap<>();
            credentialSubject.put("id", jwt.getSubject());
            jwt.getClaims().forEach((key, value) -> {
                if (!SKIP_CLAIMS.contains(key) && value instanceof String) {
                    credentialSubject.put(key, value);
                }
            });

            onboardingStateService.completeIssuerEnrollmentWithKeycloak(credentialSubject, didInfo.did(), privateJwk);
        } catch (Exception e) {
            LOGGER.error("Enrollment failed for realm {}", realm, e);
            return ResponseEntity.internalServerError().build();
        }

        return ResponseEntity.ok(onboardingStateService.getIssuerQr(null));
    }
}
