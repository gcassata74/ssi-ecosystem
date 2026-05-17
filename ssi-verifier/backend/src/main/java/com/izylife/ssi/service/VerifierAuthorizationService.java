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
import com.izylife.ssi.dto.CredentialPreviewDto;
import com.izylife.ssi.dto.VerifyPresentationResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class VerifierAuthorizationService {

    private static final Duration DEFAULT_CODE_TTL = Duration.ofMinutes(5);

    private final Cache<String, AuthorizationCodeRecord> codes = Caffeine.newBuilder()
            .expireAfterWrite(DEFAULT_CODE_TTL)
            .build();

    public AuthorizationCodeRecord issueCode(Oidc4VpRequestService.AuthorizationSession session,
                                             VerifyPresentationResponse verification) {
        if (session.redirectUri() == null || session.redirectUri().isBlank()) {
            throw new IllegalStateException("Missing redirectUri for verifier authorization session");
        }
        String code = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(DEFAULT_CODE_TTL);
        String oauthState = session.authorizationState();
        if (oauthState == null || oauthState.isBlank()) {
            oauthState = session.state();
        }

        AuthorizationCodeRecord record = new AuthorizationCodeRecord(
                code,
                oauthState,
                session.redirectUri(),
                session.portalClientId(),
                verification.getHolderDid(),
                verification.getCredentialPreview(),
                expiresAt
        );
        codes.put(code, record);
        return record;
    }

    public Optional<AuthorizationCodeRecord> consumeCode(String code) {
        AuthorizationCodeRecord record = codes.getIfPresent(code);
        if (record == null || record.isExpired()) {
            codes.invalidate(code);
            return Optional.empty();
        }
        codes.invalidate(code);
        return Optional.of(record);
    }

    public record AuthorizationCodeRecord(String code,
                                          String state,
                                          String redirectUri,
                                          String clientId,
                                          String holderDid,
                                          CredentialPreviewDto credentialPreview,
                                          Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
