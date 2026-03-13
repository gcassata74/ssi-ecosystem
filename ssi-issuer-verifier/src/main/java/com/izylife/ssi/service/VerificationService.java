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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.dto.CredentialPreviewDto;
import com.izylife.ssi.dto.VerifyPresentationRequest;
import com.izylife.ssi.dto.VerifyPresentationResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            onboardingStateService.clearVerifiedCredential();
            onboardingStateService.publishVerifierError("Wallet has no verifiable credential to satisfy the request. Please issue the credential first.");
            VerifyPresentationResponse response = new VerifyPresentationResponse(false, null, "Wallet has no verifiable credential to satisfy the request. Please issue the credential first.");
            response.setWalletHasNoCredential(true);
            return response;
        }
        if (!challengeMatches(presentation, request.getChallenge())) {
            onboardingStateService.clearVerifiedCredential();
            onboardingStateService.showVerifierQr();
            onboardingStateService.publishVerifierError("Presentation challenge does not match the verifier request.");
            return new VerifyPresentationResponse(false, null, "Presentation challenge does not match the verifier request.");
        }
        if (!submissionMatchesDefinition(request)) {
            onboardingStateService.clearVerifiedCredential();
            onboardingStateService.showVerifierQr();
            onboardingStateService.publishVerifierError("Presentation submission mapping does not satisfy the definition requirements.");
            return new VerifyPresentationResponse(false, null, "Presentation submission mapping does not satisfy the definition requirements.");
        }

            String holderDid = extractHolderDid(presentation);
            CredentialPreviewDto preview = buildCredentialPreview(presentation);
            onboardingStateService.recordVerifiedCredential(preview);
            VerifyPresentationResponse response = new VerifyPresentationResponse(true, holderDid, "Presentation validated successfully.");
            response.setCredentialPreview(preview);
            return response;
        } catch (IllegalArgumentException ex) {
            onboardingStateService.clearVerifiedCredential();
            onboardingStateService.showVerifierQr();
            onboardingStateService.publishVerifierError("Invalid presentation encoding: " + ex.getMessage());
            return new VerifyPresentationResponse(false, null, "Invalid presentation encoding: " + ex.getMessage());
        } catch (Exception ex) {
            onboardingStateService.clearVerifiedCredential();
            onboardingStateService.showVerifierQr();
            onboardingStateService.publishVerifierError("Failed to process presentation: " + ex.getMessage());
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

    private CredentialPreviewDto buildCredentialPreview(JsonNode presentation) {
        JsonNode credentials = presentation.path("verifiableCredential");
        if (!credentials.isArray() || credentials.isEmpty()) {
            return null;
        }

        JsonNode credentialNode = credentials.get(0);
        if (credentialNode == null || credentialNode.isNull()) {
            return null;
        }

        Map<String, Object> credentialMap;
        String rawRepresentation;

        if (credentialNode.isTextual()) {
            String jwt = credentialNode.asText();
            credentialMap = decodeJwt(jwt);
            rawRepresentation = jwt;
        } else if (credentialNode.isObject()) {
            credentialMap = objectMapper.convertValue(credentialNode, new TypeReference<Map<String, Object>>() {});
            rawRepresentation = credentialNode.toString();
        } else {
            return null;
        }

        if (credentialMap == null) {
            return null;
        }

        Map<String, Object> vcSection = extractVcSection(credentialMap);
        CredentialPreviewDto preview = new CredentialPreviewDto();
        preview.setRawJson(rawRepresentation);

        Object issuer = vcSection.getOrDefault("issuer", credentialMap.get("issuer"));
        if (issuer == null) {
            issuer = credentialMap.get("iss");
        }
        if (issuer instanceof String issuerId) {
            preview.setIssuerId(issuerId);
        } else if (issuer instanceof Map<?, ?> issuerMap) {
            Object id = issuerMap.get("id");
            Object name = issuerMap.get("name");
            if (id instanceof String idString) {
                preview.setIssuerId(idString);
            }
            if (name instanceof String nameString) {
                preview.setIssuerName(nameString);
            }
        }

        Object type = vcSection.get("type");
        if (type instanceof List<?> typeList) {
            List<String> types = typeList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
            preview.setType(types);
        }

        Object subjectObj = vcSection.get("credentialSubject");
        if (subjectObj instanceof Map<?, ?> subjectMap) {
            Map<String, Object> subject = objectMapper.convertValue(subjectMap, new TypeReference<Map<String, Object>>() {});
            preview.setSubject(subject);
        }

        return preview;
    }

    private Map<String, Object> decodeJwt(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            return null;
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            return objectMapper.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractVcSection(Map<String, Object> credentialMap) {
        Object vc = credentialMap.get("vc");
        if (vc instanceof Map<?, ?> vcMap) {
            return (Map<String, Object>) vcMap;
        }
        return credentialMap;
    }
}
