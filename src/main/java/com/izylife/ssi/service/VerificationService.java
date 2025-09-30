package com.izylife.ssi.service;

import com.izylife.ssi.dto.VerifyPresentationRequest;
import com.izylife.ssi.dto.VerifyPresentationResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

@Service
public class VerificationService {

    private static final String NO_CREDENTIAL_MARKER = "hascredential=false";

    private final OnboardingStateService onboardingStateService;

    public VerificationService(OnboardingStateService onboardingStateService) {
        this.onboardingStateService = onboardingStateService;
    }

    public VerifyPresentationResponse verifyPresentation(VerifyPresentationRequest request) {
        try {
            String decoded = new String(Base64.getDecoder().decode(request.getPresentationPayload()), StandardCharsets.UTF_8);
            if (walletReportedMissingCredential(decoded)) {
                onboardingStateService.showSpidAuth();
                VerifyPresentationResponse response = new VerifyPresentationResponse(false, null, "Wallet has no verifiable credentials to satisfy the request.");
                response.setWalletHasNoCredential(true);
                return response;
            }
            boolean challengeMatches = decoded.contains(request.getChallenge());
            if (!challengeMatches) {
                onboardingStateService.showVerifierQr();
                return new VerifyPresentationResponse(false, null, "Presentation challenge does not match the verifier request.");
            }
            // crude parsing for demo purposes
            String holderDid = extractHolderDid(decoded);
            onboardingStateService.showVerifierQr();
            return new VerifyPresentationResponse(true, holderDid, "Presentation validated successfully.");
        } catch (IllegalArgumentException ex) {
            onboardingStateService.showVerifierQr();
            return new VerifyPresentationResponse(false, null, "Invalid presentation encoding: " + ex.getMessage());
        }
    }

    private String extractHolderDid(String payload) {
        String key = "subjectDid=";
        int index = payload.indexOf(key);
        if (index < 0) {
            return "did:example:unknown";
        }
        int end = payload.indexOf(',', index);
        if (end < 0) {
            end = payload.length();
        }
        return payload.substring(index + key.length(), end).trim();
    }

    private boolean walletReportedMissingCredential(String payload) {
        if (payload == null || payload.isBlank()) {
            return true;
        }
        String lowerCase = payload.toLowerCase(Locale.ROOT);
        return lowerCase.contains(NO_CREDENTIAL_MARKER)
                || lowerCase.contains("\"hascredential\":false")
                || lowerCase.contains("wallethasnocredential")
                || lowerCase.contains("no_credential")
                || lowerCase.contains("novc");
    }
}
