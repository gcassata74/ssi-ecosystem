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
