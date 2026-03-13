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

package com.izylife.ssi.controller;

import com.izylife.ssi.dto.oidc4vci.TokenResponse;
import com.izylife.ssi.service.Oidc4vciService;
import com.izylife.ssi.service.Oidc4vciService.AccessToken;
import com.izylife.ssi.service.Oidc4vciService.AuthorizationGrant;
import com.izylife.ssi.service.Oidc4vciService.CredentialOfferRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping(path = "/oidc4vci/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class Oidc4vciTokenController {

    private final Oidc4vciService oidc4vciService;

    public Oidc4vciTokenController(Oidc4vciService oidc4vciService) {
        this.oidc4vciService = oidc4vciService;
    }

    @PostMapping
    public ResponseEntity<?> token(@RequestBody MultiValueMap<String, String> formData) {
        String grantType = formData.getFirst("grant_type");
        if (grantType == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "grant_type is required"));
        }

        return switch (grantType) {
            case "authorization_code" -> handleAuthorizationCode(formData);
            case "urn:ietf:params:oauth:grant-type:pre-authorized_code" -> handlePreAuthorizedCode(formData);
            default -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("unsupported_grant_type", "Grant type not supported"));
        };
    }

    private ResponseEntity<?> handleAuthorizationCode(MultiValueMap<String, String> formData) {
        String code = formData.getFirst("code");
        String clientId = formData.getFirst("client_id");
        String redirectUri = formData.getFirst("redirect_uri");

        if (code == null || clientId == null || redirectUri == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "code, client_id and redirect_uri are required"));
        }

        Optional<AuthorizationGrant> grantOptional = oidc4vciService.consumeAuthorizationCode(code);
        if (grantOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_grant", "Authorization code is invalid or expired"));
        }

        AuthorizationGrant grant = grantOptional.get();
        if (!clientId.equals(grant.clientId()) || !redirectUri.equals(grant.redirectUri())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_grant", "Authorization code does not match client or redirect URI"));
        }

        Optional<CredentialOfferRecord> offerOptional = oidc4vciService.resolveOffer(grant.offerId());
        if (offerOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_grant", "Credential offer expired"));
        }

        AccessToken accessToken = oidc4vciService.issueAccessToken(offerOptional.get());
        return ResponseEntity.ok(toResponse(accessToken));
    }

    private ResponseEntity<?> handlePreAuthorizedCode(MultiValueMap<String, String> formData) {
        String code = formData.getFirst("pre-authorized_code");
        String userPin = formData.getFirst("user_pin");
        if (code == null || code.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "pre-authorized_code is required"));
        }

        Optional<CredentialOfferRecord> offerOptional = oidc4vciService.consumePreAuthorizedCode(code);
        if (offerOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_grant", "Pre-authorized code is invalid or expired"));
        }

        CredentialOfferRecord offer = offerOptional.get();
        if (offer.userPinRequired()) {
            if (userPin == null || userPin.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_grant", "user_pin is required for this offer"));
            }
        }

        AccessToken accessToken = oidc4vciService.issueAccessToken(offer);
        return ResponseEntity.ok(toResponse(accessToken));
    }

    private TokenResponse toResponse(AccessToken accessToken) {
        Instant now = Instant.now();
        TokenResponse response = new TokenResponse();
        response.setAccessToken(accessToken.value());
        response.setExpiresIn(Math.max(1, Duration.between(now, accessToken.expiresAt()).toSeconds()));
        response.setCNonce(accessToken.cNonce());
        response.setCNonceExpiresIn(Math.max(1, Duration.between(now, accessToken.cNonceExpiresAt()).toSeconds()));
        response.setTokenType("bearer");
        return response;
    }

    private ErrorResponse error(String code, String description) {
        return new ErrorResponse(code, description);
    }

    private record ErrorResponse(String error, String error_description) {
    }
}
