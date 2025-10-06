package com.izylife.ssi.controller;

import com.izylife.ssi.dto.oidc4vci.CredentialOfferResponse;
import com.izylife.ssi.service.Oidc4vciService;
import com.izylife.ssi.service.Oidc4vciService.CredentialOfferRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/oidc4vci/credential-offers", produces = MediaType.APPLICATION_JSON_VALUE)
public class Oidc4vciOfferController {

    private final Oidc4vciService oidc4vciService;

    public Oidc4vciOfferController(Oidc4vciService oidc4vciService) {
        this.oidc4vciService = oidc4vciService;
    }

    @GetMapping("/{offerId}")
    public ResponseEntity<CredentialOfferResponse> getCredentialOffer(@PathVariable String offerId) {
        return oidc4vciService.findOfferById(offerId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private CredentialOfferResponse toResponse(CredentialOfferRecord record) {
        return new CredentialOfferResponse(oidc4vciService.buildCredentialOffer(record));
    }
}
