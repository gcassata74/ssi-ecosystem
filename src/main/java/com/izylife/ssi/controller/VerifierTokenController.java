package com.izylife.ssi.controller;

import com.izylife.ssi.service.VerifierAuthorizationService;
import com.izylife.ssi.service.VerifierAuthorizationService.AuthorizationCodeRecord;
import com.izylife.ssi.service.VerifierTokenService;
import com.izylife.ssi.service.VerifierTokenService.TokenResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Map;

@RestController
@RequestMapping(path = "/oauth2", produces = MediaType.APPLICATION_JSON_VALUE)
public class VerifierTokenController {

    private final VerifierAuthorizationService authorizationService;
    private final VerifierTokenService tokenService;

    public VerifierTokenController(VerifierAuthorizationService authorizationService,
                                   VerifierTokenService tokenService) {
        this.authorizationService = authorizationService;
        this.tokenService = tokenService;
    }

    @PostMapping(path = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> exchangeAuthorizationCode(@RequestParam MultiValueMap<String, String> formData) {
        String grantType = firstValue(formData, "grant_type");
        if (!"authorization_code".equals(grantType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported_grant_type");
        }

        String code = firstValue(formData, "code");
        String redirectUri = firstValue(formData, "redirect_uri");
        String clientId = firstValue(formData, "client_id");

        if (code == null || redirectUri == null || clientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_request");
        }

        AuthorizationCodeRecord record = authorizationService.consumeCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_grant"));

        if (!redirectUri.equals(record.redirectUri())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_grant");
        }
        if (record.clientId() != null && !record.clientId().isBlank() && !clientId.equals(record.clientId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_client");
        }

        TokenResult tokenResult = tokenService.createTokenResponse(record);
        return ResponseEntity.ok(tokenResult.toResponseMap());
    }

    private String firstValue(MultiValueMap<String, String> formData, String key) {
        return formData != null ? formData.getFirst(key) : null;
    }
}
