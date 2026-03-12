package com.izylife.ssi.security.admin;

import com.izylife.ssi.config.AppProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class AdminTokenService {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenService.class);
    public static final String COOKIE_NAME = "ADMIN_TOKEN";

    private final byte[] secret;
    private final Duration tokenTtl;

    public AdminTokenService(AppProperties properties) {
        AppProperties.AdminProperties admin = properties.getAdmin();
        Assert.notNull(admin, "Admin properties must be configured");
        String secretValue = admin.getTokenSecret();
        if (secretValue == null || secretValue.length() < 32) {
            throw new IllegalStateException("Admin token secret must be at least 32 characters long");
        }
        this.secret = secretValue.getBytes(StandardCharsets.UTF_8);
        this.tokenTtl = admin.getTokenTtl() != null && !admin.getTokenTtl().isZero()
                ? admin.getTokenTtl()
                : Duration.ofHours(8);
    }

    public String createToken(String username) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(tokenTtl);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .claim("typ", "admin-session")
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to create admin session token", ex);
        }
    }

    public Optional<AdminPrincipal> parseToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                log.debug("Admin token signature verification failed");
                return Optional.empty();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime == null || expirationTime.before(new Date())) {
                log.debug("Admin token has expired");
                return Optional.empty();
            }

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new AdminPrincipal(subject));
        } catch (ParseException | JOSEException ex) {
            log.debug("Unable to parse admin token", ex);
            return Optional.empty();
        }
    }

    public Duration tokenTtl() {
        return tokenTtl;
    }
}
