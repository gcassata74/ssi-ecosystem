/*
 * SSI Verifier
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

import com.fasterxml.jackson.databind.JsonNode;
import com.izylife.ssi.service.Oidc4VpRequestService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/presentation-definition", produces = MediaType.APPLICATION_JSON_VALUE)
public class PresentationDefinitionController {

    private final Oidc4VpRequestService requestService;

    public PresentationDefinitionController(Oidc4VpRequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping
    public ResponseEntity<JsonNode> getCurrent() {
        return ResponseEntity.ok(requestService.getPresentationDefinition());
    }

    @PostMapping("/reload")
    public ResponseEntity<JsonNode> reload() {
        return ResponseEntity.ok(requestService.reloadPresentationDefinition());
    }
}
