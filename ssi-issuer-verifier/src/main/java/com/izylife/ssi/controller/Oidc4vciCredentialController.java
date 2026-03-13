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

import com.izylife.ssi.dto.oidc4vci.CredentialRequest;
import com.izylife.ssi.dto.oidc4vci.CredentialResponse;
import com.izylife.ssi.service.Oidc4vciService;
import com.izylife.ssi.service.Oidc4vciService.AccessTokenGrant;
import com.izylife.ssi.service.Oidc4vciService.CredentialOfferRecord;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping(path = "/oidc4vci/credential", produces = MediaType.APPLICATION_JSON_VALUE)
public class Oidc4vciCredentialController {

    private static final String SUPPORTED_FORMAT = "jwt_vc_json";

    private final Oidc4vciService oidc4vciService;

    public Oidc4vciCredentialController(Oidc4vciService oidc4vciService) {
        this.oidc4vciService = oidc4vciService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> issueCredential(@RequestHeader HttpHeaders headers,
                                             @RequestBody CredentialRequest credentialRequest) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.toLowerCase().startsWith("bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("invalid_token", "Bearer token missing"));
        }

        String accessTokenValue = authorization.substring(7).trim();
        Optional<AccessTokenGrant> grantOptional = oidc4vciService.resolveAccessToken(accessTokenValue);
        if (grantOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("invalid_token", "Access token is invalid or expired"));
        }

        AccessTokenGrant grant = grantOptional.get();
        Optional<CredentialOfferRecord> offerOptional = oidc4vciService.resolveOffer(grant.offerId());
        if (offerOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "Credential offer has expired"));
        }

        if (credentialRequest == null || credentialRequest.getFormat() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "format is required"));
        }
        if (!SUPPORTED_FORMAT.equals(credentialRequest.getFormat())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("unsupported_credential_format", "Only jwt_vc_json is supported"));
        }

        CredentialRequest.ProofRequest proof = credentialRequest.getProof();
        if (proof == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "proof is required"));
        }
        if (proof.getProofType() == null || proof.getProofType().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "proof.proof_type is required"));
        }
        if (!"jwt".equalsIgnoreCase(proof.getProofType())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "Only proof_type=jwt is supported"));
        }
        if (proof.getJwt() == null || proof.getJwt().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "proof.jwt must be provided"));
        }

        CredentialOfferRecord offer = offerOptional.get();
        String configurationId = credentialRequest.getCredentialConfigurationId();
        if (configurationId == null || configurationId.isBlank()) {
            configurationId = offer.credentialConfigurationIds().get(0);
        }
        if (!offer.credentialConfigurationIds().contains(configurationId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "Unknown credential_configuration_id"));
        }

        CredentialRequest.CredentialDefinitionRequest definition = credentialRequest.getCredentialDefinition();
        if (definition != null) {
            List<String> types = definition.getType();
            if (types == null || types.isEmpty() || !types.contains("PublicAuthorityStaffCredential")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("invalid_request", "credential_definition.type must include PublicAuthorityStaffCredential"));
            }
        }

        String credentialJwt = oidc4vciService.buildStaffCredentialJwt(offer.profile());

        Instant now = Instant.now();
        CredentialResponse response = new CredentialResponse();
        response.setFormat(SUPPORTED_FORMAT);
        response.setCredential(credentialJwt);
        response.setCNonce(grant.cNonce());
        response.setCNonceExpiresIn(Math.max(1L, Duration.between(now, grant.cNonceExpiresAt()).toSeconds()));

        return ResponseEntity.ok(response);
    }

    private ErrorResponse error(String error, String description) {
        return new ErrorResponse(error, description);
    }

    private record ErrorResponse(String error, String error_description) {
    }
}
