package com.izylife.ssi.controller;

import com.izylife.ssi.service.Oidc4vciService;
import com.izylife.ssi.service.Oidc4vciService.AuthorizationCode;
import com.izylife.ssi.service.Oidc4vciService.AuthorizationRequest;
import com.izylife.ssi.service.Oidc4vciService.CredentialOfferRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping(path = "/oidc4vci", produces = MediaType.APPLICATION_JSON_VALUE)
public class Oidc4vciAuthorizationController {

    private final Oidc4vciService oidc4vciService;

    public Oidc4vciAuthorizationController(Oidc4vciService oidc4vciService) {
        this.oidc4vciService = oidc4vciService;
    }

    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(@RequestParam MultiValueMap<String, String> parameters) {
        String responseType = parameters.getFirst("response_type");
        String clientId = parameters.getFirst("client_id");
        String redirectUri = parameters.getFirst("redirect_uri");
        String state = parameters.getFirst("state");
        String issuerState = parameters.getFirst("issuer_state");

        if (!"code".equals(responseType) || clientId == null || clientId.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new OidcErrorResponse("invalid_request", "Missing or invalid authorization request parameters."));
        }

        if (issuerState == null || issuerState.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new OidcErrorResponse("invalid_request", "issuer_state is required"));
        }

        Optional<CredentialOfferRecord> offerOptional = oidc4vciService.findOfferByIssuerState(issuerState);
        if (offerOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new OidcErrorResponse("invalid_grant", "Unknown issuer_state"));
        }

        AuthorizationCode authorizationCode = oidc4vciService.issueAuthorizationCode(
                offerOptional.get(),
                new AuthorizationRequest(clientId, redirectUri)
        );

        URI redirectLocation = UriComponentsBuilder
                .fromUriString(redirectUri)
                .queryParam("code", authorizationCode.code())
                .queryParamIfPresent("state", Optional.ofNullable(state))
                .build(true)
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND).location(redirectLocation).build();
    }

    private record OidcErrorResponse(String error, String errorDescription) {
    }
}
