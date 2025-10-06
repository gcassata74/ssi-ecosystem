package com.izylife.ssi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.dto.VerifyPresentationRequest;
import com.izylife.ssi.dto.VerifyPresentationResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class VerificationService {

    private static final String NO_CREDENTIAL_MARKER = "hascredential=false";

    private final OnboardingStateService onboardingStateService;
    private final ObjectMapper objectMapper;
    private final Set<String> requiredDescriptorIds;
    private final String expectedDefinitionId;

    public VerificationService(
            OnboardingStateService onboardingStateService,
            ObjectMapper objectMapper,
            Oidc4VpRequestService oidc4VpRequestService,
            AppProperties appProperties
    ) {
        this.onboardingStateService = onboardingStateService;
        this.objectMapper = objectMapper;
        this.requiredDescriptorIds = oidc4VpRequestService.getInputDescriptorIds();
        this.expectedDefinitionId = appProperties.getVerifier().getPresentationDefinitionId();
    }

    public VerifyPresentationResponse verifyPresentation(VerifyPresentationRequest request) {
        String requestState = request.getState();
        boolean stateMatches = onboardingStateService.isActiveAuthorizationState(requestState);
        if (!stateMatches) {
            return new VerifyPresentationResponse(false, null, "Presentation response does not match the active verification request. Please rescan the QR code.");
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(request.getPresentationPayload()), StandardCharsets.UTF_8);
            JsonNode presentation = objectMapper.readTree(decoded);

            if (walletReportedMissingCredential(decoded) || !hasCredentials(presentation)) {
                onboardingStateService.showIssuerQr();
                VerifyPresentationResponse response = new VerifyPresentationResponse(false, null, "Wallet has no verifiable credential to satisfy the request. Please issue the credential first.");
                response.setWalletHasNoCredential(true);
                return response;
            }
            if (!challengeMatches(presentation, request.getChallenge())) {
                onboardingStateService.showVerifierQr();
                return new VerifyPresentationResponse(false, null, "Presentation challenge does not match the verifier request.");
            }
            if (!submissionMatchesDefinition(request)) {
                onboardingStateService.showVerifierQr();
                return new VerifyPresentationResponse(false, null, "Presentation submission mapping does not satisfy the definition requirements.");
            }

            String holderDid = extractHolderDid(presentation);
            onboardingStateService.showVerifierQr();
            return new VerifyPresentationResponse(true, holderDid, "Presentation validated successfully.");
        } catch (IllegalArgumentException ex) {
            onboardingStateService.showVerifierQr();
            return new VerifyPresentationResponse(false, null, "Invalid presentation encoding: " + ex.getMessage());
        } catch (Exception ex) {
            onboardingStateService.showVerifierQr();
            return new VerifyPresentationResponse(false, null, "Failed to process presentation: " + ex.getMessage());
        }
    }

    private boolean hasCredentials(JsonNode presentation) {
        JsonNode credentials = presentation.path("verifiableCredential");
        return credentials.isArray() && credentials.size() > 0;
    }

    private boolean challengeMatches(JsonNode presentation, String expectedChallenge) {
        if (expectedChallenge == null || expectedChallenge.isBlank()) {
            return true;
        }
        JsonNode proofNode = presentation.path("proof");
        String challenge = proofNode.path("challenge").asText(null);
        return expectedChallenge.equals(challenge);
    }

    private boolean submissionMatchesDefinition(VerifyPresentationRequest request) {
        if (request.getPresentationSubmission() == null) {
            return false;
        }
        try {
            JsonNode submission = objectMapper.readTree(request.getPresentationSubmission());
            if (!expectedDefinitionId.equals(submission.path("definition_id").asText(null))) {
                return false;
            }

            JsonNode descriptorMap = submission.path("descriptor_map");
            if (!descriptorMap.isArray() || descriptorMap.isEmpty()) {
                return false;
            }

            Set<String> mappedIds = new LinkedHashSet<>();
            for (JsonNode descriptor : descriptorMap) {
                if (descriptor == null || !descriptor.isObject()) {
                    return false;
                }
                String id = descriptor.path("id").asText(null);
                if (id == null || id.isBlank()) {
                    return false;
                }
                if (!requiredDescriptorIds.contains(id)) {
                    return false;
                }
                if (!mappedIds.add(id)) {
                    return false;
                }
                String format = descriptor.path("format").asText(null);
                String path = descriptor.path("path").asText(null);
                if (format == null || format.isBlank() || path == null || path.isBlank()) {
                    return false;
                }
            }

            return mappedIds.containsAll(requiredDescriptorIds);
        } catch (Exception ex) {
            return false;
        }
    }

    private String extractHolderDid(JsonNode presentation) {
        JsonNode holderNode = presentation.get("holder");
        if (holderNode != null && holderNode.isTextual()) {
            return holderNode.asText();
        }
        JsonNode credentialArray = presentation.path("verifiableCredential");
        if (credentialArray.isArray() && credentialArray.size() > 0) {
            JsonNode firstCredential = credentialArray.get(0);
            JsonNode subject = firstCredential.path("credentialSubject");
            if (subject.isObject()) {
                JsonNode id = subject.get("id");
                if (id != null && id.isTextual()) {
                    return id.asText();
                }
            }
        }
        return "did:example:unknown";
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
