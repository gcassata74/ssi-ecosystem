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
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping(path = "/oidc4vp", produces = MediaType.APPLICATION_JSON_VALUE)
public class Oidc4VpResponseController {

    private final VerificationService verificationService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final Oidc4VpRequestService oidc4VpRequestService;

    public Oidc4VpResponseController(
            VerificationService verificationService,
            AppProperties appProperties,
            ObjectMapper objectMapper,
            Oidc4VpRequestService oidc4VpRequestService
    ) {
        this.verificationService = verificationService;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.oidc4VpRequestService = oidc4VpRequestService;
    }

    @PostMapping(path = "/responses", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public VerifyPresentationResponse handleFormSubmission(@RequestParam MultiValueMap<String, String> formData) {
        Oidc4VpSubmission submission = fromForm(formData);
        return processSubmission(submission);
    }

    @PostMapping(path = "/responses", consumes = MediaType.APPLICATION_JSON_VALUE)
    public VerifyPresentationResponse handleJsonSubmission(@RequestBody Oidc4VpSubmission submission) {
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

        String vpTokenPayload = normalizeJson(submission.getVpToken(), "vp_token");
        JsonNode presentationSubmission = parseJson(submission.getPresentationSubmission(), "presentation_submission");
        validateDescriptorDefinition(presentationSubmission);

        String receivedNonce = submission.getNonce();
        if (!StringUtils.hasText(receivedNonce)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing nonce parameter");
        }
        if (!receivedNonce.equals(session.nonce())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nonce does not match the authorization request");
        }

        VerifyPresentationRequest request = new VerifyPresentationRequest();
        request.setPresentationPayload(encodeToBase64(vpTokenPayload));
        request.setChallenge(receivedNonce);
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
        String expectedDefinitionId = appProperties.getVerifier().getPresentationDefinitionId();
        if (definitionIdNode == null || !expectedDefinitionId.equals(definitionIdNode.asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.definition_id does not match the presentation definition");
        }

        JsonNode descriptorMap = presentationSubmission.get("descriptor_map");
        if (descriptorMap == null || !descriptorMap.isArray() || descriptorMap.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "presentation_submission.descriptor_map must include at least one entry");
        }

        // noop validation hook for descriptor contents in this demo implementation
        // Real implementation would resolve JSONPath entries against the submitted VP.
    }

    private Oidc4VpSubmission fromForm(MultiValueMap<String, String> formData) {
        Oidc4VpSubmission submission = new Oidc4VpSubmission();
        submission.setVpToken(firstValue(formData, "vp_token"));
        submission.setPresentationSubmission(firstValue(formData, "presentation_submission"));
        submission.setPresentationPayload(firstValue(formData, "presentation_payload"));
        submission.setState(firstValue(formData, "state"));
        submission.setClientId(firstValue(formData, "client_id"));
        submission.setNonce(firstValue(formData, "nonce"));
        return submission;
    }

    private String firstValue(MultiValueMap<String, String> formData, String key) {
        return formData != null ? formData.getFirst(key) : null;
    }

    private String normalizeJson(String raw, String parameterName) {
        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing " + parameterName + " parameter");
        }
        JsonNode parsed = parseJson(raw, parameterName);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON in " + parameterName + " parameter");
        }
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to normalise " + parameterName + " JSON", ex);
        }
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
}
