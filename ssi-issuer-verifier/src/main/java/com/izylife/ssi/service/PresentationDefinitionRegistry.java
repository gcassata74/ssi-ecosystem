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
import com.izylife.ssi.model.PresentationDefinitionDocument;
import com.izylife.ssi.repository.PresentationDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class PresentationDefinitionRegistry {

    private static final Logger log = LoggerFactory.getLogger(PresentationDefinitionRegistry.class);

    private final PresentationDefinitionRepository definitionRepository;
    private final KeycloakPresentationDefinitionService keycloakPresentationDefinitionService;
    private final ObjectMapper objectMapper;

    public PresentationDefinitionRegistry(PresentationDefinitionRepository definitionRepository,
                                          KeycloakPresentationDefinitionService keycloakPresentationDefinitionService,
                                          ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.keycloakPresentationDefinitionService = keycloakPresentationDefinitionService;
        this.objectMapper = objectMapper;
    }

    public Optional<JsonNode> find(String definitionId) {
        Optional<JsonNode> fromDatabase = definitionRepository.findByDefinitionId(definitionId)
                .map(this::toJsonNode);
        if (fromDatabase.isPresent()) {
            return fromDatabase;
        }
        return keycloakPresentationDefinitionService.getPresentationDefinition(definitionId);
    }

    public Optional<byte[]> findBytes(String definitionId) {
        return find(definitionId).map(node -> node.toString().getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode toJsonNode(PresentationDefinitionDocument document) {
        String json = document.getDefinitionJson();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            log.warn("Unable to parse presentation definition '{}' from database", document.getDefinitionId(), e);
            return null;
        }
    }
}
