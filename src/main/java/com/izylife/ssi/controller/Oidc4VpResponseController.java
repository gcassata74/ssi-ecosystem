package com.izylife.ssi.controller;

import com.izylife.ssi.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.dto.Oidc4VpSubmission;
import com.izylife.ssi.dto.VerifyPresentationRequest;
import com.izylife.ssi.dto.VerifyPresentationResponse;
import com.izylife.ssi.service.Oidc4VpRequestService;
import com.izylife.ssi.service.Oidc4VpRequestService.AuthorizationSession;
import com.izylife.ssi.service.VerificationService;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

@RestController
@RequestMapping(path = "/oidc4vp", produces = MediaType.APPLICATION_JSON_VALUE)
public class Oidc4VpResponseController {

    private final VerificationService verificationService;
    private final ObjectMapper objectMapper;
    private final Oidc4VpRequestService oidc4VpRequestService;
    private final String expectedDefinitionId;
    private final Set<String> requiredDescriptorIds;

    public Oidc4VpResponseController(
            VerificationService verificationService,
            AppProperties appProperties,
            ObjectMapper objectMapper,
            Oidc4VpRequestService oidc4VpRequestService
    ) {
        this.verificationService = verificationService;
        this.objectMapper = objectMapper;
        this.oidc4VpRequestService = oidc4VpRequestService;
        this.expectedDefinitionId = appProperties.getVerifier().getPresentationDefinitionId();
        this.requiredDescriptorIds = oidc4VpRequestService.getInputDescriptorIds();
    }

    @PostMapping(path = "/responses", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public VerifyPresentationResponse handleFormSubmission(@RequestParam MultiValueMap<String, String> formData) {
        Oidc4VpSubmission submission = fromForm(formData);
        return processSubmission(submission);
    }

    private VerifyPresentationResponse processSubmission(Oidc4VpSubmission submission) {
        if (submission == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing OIDC4VP submission body");
        }

        String state = submission.getState();
        if (!StringUtils.hasText(state)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing state parameter");
        }

        AuthorizationSession session = oidc4VpRequestService.resolveSession(state)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or expired authorization request"));

        VpTokenPayload vpToken = extractVpTokenPayload(submission.getVpToken());
        JsonNode presentationSubmission = parseJson(submission.getPresentationSubmission(), "presentation_submission");
        validateDescriptorDefinition(presentationSubmission);

        String receivedNonce = vpToken.nonce();
        if (!StringUtils.hasText(receivedNonce)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing nonce in vp_token");
        }
        String expectedNonce = session.nonce();
        if (StringUtils.hasText(receivedNonce) && !receivedNonce.equals(expectedNonce)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nonce parameter does not match the authorization request");
        }

        VerifyPresentationRequest request = new VerifyPresentationRequest();
        request.setPresentationPayload(encodeToBase64(vpToken.presentation()));
        request.setChallenge(expectedNonce);
        try {
            request.setPresentationSubmission(objectMapper.writeValueAsString(presentationSubmission));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to serialise presentation_submission", ex);
        }
        request.setState(state);

        VerifyPresentationResponse response = verificationService.verifyPresentation(request);
        oidc4VpRequestService.consumeSession(state);
        return response;
    }

    private void validateDescriptorDefinition(JsonNode presentationSubmission) {
        if (presentationSubmission == null || presentationSubmission.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing presentation_submission parameter");
        }

        JsonNode definitionIdNode = presentationSubmission.get("definition_id");
        if (definitionIdNode == null || !expectedDefinitionId.equals(definitionIdNode.asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.definition_id does not match the presentation definition");
        }

        JsonNode descriptorMap = presentationSubmission.get("descriptor_map");
        if (descriptorMap == null || !descriptorMap.isArray() || descriptorMap.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.descriptor_map must include at least one entry");
        }

        Set<String> mappedIds = new LinkedHashSet<>();
        for (JsonNode descriptor : descriptorMap) {
            if (descriptor == null || !descriptor.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.descriptor_map entries must be JSON objects");
            }
            String id = descriptor.path("id").asText(null);
            if (!StringUtils.hasText(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.descriptor_map entries must include a non-empty id");
            }
            if (!requiredDescriptorIds.contains(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.descriptor_map id is not part of the requested definition: " + id);
            }
            if (!mappedIds.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.descriptor_map contains duplicate mapping for id: " + id);
            }

            if (!StringUtils.hasText(descriptor.path("format").asText(null))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.descriptor_map entries must include a non-empty format");
            }
            if (!StringUtils.hasText(descriptor.path("path").asText(null))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.descriptor_map entries must include a non-empty path");
            }
        }

        Set<String> missingIds = new LinkedHashSet<>(requiredDescriptorIds);
        missingIds.removeAll(mappedIds);
        if (!missingIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.descriptor_map missing mappings for: " + String.join(", ", missingIds));
        }

        // noop validation hook for descriptor contents in this demo implementation
        // Real implementation would resolve JSONPath entries against the submitted VP.
    }

    private Oidc4VpSubmission fromForm(MultiValueMap<String, String> formData) {
        Oidc4VpSubmission submission = new Oidc4VpSubmission();
        submission.setVpToken(firstValue(formData, "vp_token"));
        submission.setPresentationSubmission(firstValue(formData, "presentation_submission"));
        submission.setState(firstValue(formData, "state"));
        return submission;
    }

    private String firstValue(MultiValueMap<String, String> formData, String key) {
        return formData != null ? formData.getFirst(key) : null;
    }

    private JsonNode parseJson(String raw, String parameterName) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse " + parameterName + " as JSON", ex);
        }
    }

    private String encodeToBase64(String payload) {
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private VpTokenPayload extractVpTokenPayload(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing vp_token parameter");
        }
        try {
            JWT jwt = JWTParser.parse(rawToken);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Object vpClaim = claims.getClaim("vp");
            if (vpClaim == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vp_token is missing vp claim");
            }
            JsonNode vpNode = objectMapper.valueToTree(vpClaim);
            if (vpNode == null || vpNode.isMissingNode() || vpNode.isNull()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vp_token contains an empty vp claim");
            }
            String normalizedPresentation = objectMapper.writeValueAsString(vpNode);
            String nonce = claims.getStringClaim("nonce");
            return new VpTokenPayload(normalizedPresentation, nonce);
        } catch (ParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse vp_token as JWT", ex);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to process vp_token", ex);
        }
    }

    private record VpTokenPayload(String presentation, String nonce) {
    }
}
