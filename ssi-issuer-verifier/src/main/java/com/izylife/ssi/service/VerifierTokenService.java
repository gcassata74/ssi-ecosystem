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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.service.VerifierAuthorizationService.AuthorizationCodeRecord;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class VerifierTokenService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final JWSSigner signer;
    private final JWSHeader header;
    private final String issuer;
    private final ObjectMapper objectMapper;

    public VerifierTokenService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try {
            AppProperties.SigningKeyProperties keyProperties = appProperties.getVerifier().getSigningKey();
            ECKey ecKey = buildSigningKey(keyProperties);
            this.signer = new ECDSASigner(ecKey);
            JWSAlgorithm algorithm;
            if (ecKey.getAlgorithm() instanceof JWSAlgorithm jwsAlgorithm) {
                algorithm = jwsAlgorithm;
            } else {
                String algName = ecKey.getAlgorithm() != null ? ecKey.getAlgorithm().getName() : "ES256";
                algorithm = JWSAlgorithm.parse(algName);
            }
            this.header = new JWSHeader.Builder(algorithm)
                    .keyID(ecKey.getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to initialise verifier token signer", ex);
        }
        this.issuer = Optional.ofNullable(appProperties.getVerifier())
                .map(AppProperties.VerifierProperties::getEndpoint)
                .orElse("http://localhost:9090");
    }

    public TokenResult createTokenResponse(AuthorizationCodeRecord record) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ACCESS_TOKEN_TTL);

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim("state", record.state());

        if (record.holderDid() != null && !record.holderDid().isBlank()) {
            claimsBuilder.subject(record.holderDid());
        }
        if (record.clientId() != null && !record.clientId().isBlank()) {
            claimsBuilder.audience(record.clientId());
        }
        if (record.credentialPreview() != null) {
            Map<String, Object> previewMap = objectMapper.convertValue(record.credentialPreview(), Map.class);
            claimsBuilder.claim("credential_preview", previewMap);
        }

        SignedJWT accessToken = new SignedJWT(header, claimsBuilder.build());
        try {
            accessToken.sign(signer);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Failed to sign verifier access token", ex);
        }

        return new TokenResult(accessToken.serialize(), "Bearer", ACCESS_TOKEN_TTL.toSeconds());
    }

    private static ECKey buildSigningKey(AppProperties.SigningKeyProperties properties) throws JOSEException {
        if (properties == null || properties.getKty() == null || !properties.getKty().equalsIgnoreCase("EC")) {
            throw new JOSEException("Verifier signing key must be provided and use EC");
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

    public record TokenResult(String accessToken, String tokenType, long expiresIn) {
        public Map<String, Object> toResponseMap() {
            Map<String, Object> response = new HashMap<>();
            response.put("access_token", accessToken);
            response.put("token_type", tokenType);
            response.put("expires_in", expiresIn);
            return response;
        }
    }
}
