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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves Presentation Definitions publicly so wallets can fetch them.
 *
 * Per-realm: GET /definitions/{realm}/staff-credential.json
 *   → fetches from Keycloak SPI for that realm (falls back to classpath).
 *
 * Default: GET /definitions/{id}.json
 *   → returns the globally-configured definition.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class DefinitionsController {

    private final Oidc4VpRequestService requestService;

    public DefinitionsController(Oidc4VpRequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping("/definitions/{realm}/staff-credential.json")
    public ResponseEntity<JsonNode> getDefinitionForRealm(@PathVariable("realm") String realm) {
        return ResponseEntity.ok(requestService.loadDefinitionForRealm(realm));
    }

    @GetMapping("/definitions/{definitionId}.json")
    public ResponseEntity<JsonNode> getDefinition(@PathVariable("definitionId") String definitionId) {
        return ResponseEntity.ok(requestService.getPresentationDefinition());
    }
}
