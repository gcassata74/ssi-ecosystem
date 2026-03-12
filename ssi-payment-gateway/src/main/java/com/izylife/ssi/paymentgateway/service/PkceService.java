package com.izylife.ssi.paymentgateway.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PkceService {

    private static final String METHOD = "S256";
    private final SecureRandom secureRandom = new SecureRandom();

    public PkcePair createPair() {
        byte[] codeVerifierBytes = new byte[64];
        secureRandom.nextBytes(codeVerifierBytes);
        String codeVerifier = base64Url(codeVerifierBytes);
        String codeChallenge = hashVerifier(codeVerifier);
        return new PkcePair(codeVerifier, codeChallenge, METHOD);
    }

    private String hashVerifier(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return base64Url(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
