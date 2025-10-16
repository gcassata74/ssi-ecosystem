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
