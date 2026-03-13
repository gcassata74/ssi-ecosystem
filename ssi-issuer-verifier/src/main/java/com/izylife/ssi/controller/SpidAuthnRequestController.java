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

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.service.SpidAuthnRequestStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/spid")
public class SpidAuthnRequestController {

    private static final String ATTACHMENT_FILENAME = "spid-authn-request.xml";

    private final AppProperties appProperties;
    private final SpidAuthnRequestStore spidAuthnRequestStore;

    public SpidAuthnRequestController(AppProperties appProperties,
                                      SpidAuthnRequestStore spidAuthnRequestStore) {
        this.appProperties = appProperties;
        this.spidAuthnRequestStore = spidAuthnRequestStore;
    }

    @GetMapping(value = "/authn-request", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> downloadLatestAuthnRequest() {
        AppProperties.SpidProperties spid = appProperties.getSpid();
        if (spid == null || !spid.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

return spidAuthnRequestStore.latest()
                .map(stored -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + ATTACHMENT_FILENAME)
                        .contentType(MediaType.APPLICATION_XML)
                        .lastModified(stored.capturedAt().toEpochMilli())
                        .body(stored.xml()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
