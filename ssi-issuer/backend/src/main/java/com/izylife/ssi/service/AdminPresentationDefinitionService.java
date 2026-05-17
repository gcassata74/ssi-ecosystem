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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.dto.admin.PresentationDefinitionRequest;
import com.izylife.ssi.dto.admin.PresentationDefinitionResponse;
import com.izylife.ssi.model.AdminClient;
import com.izylife.ssi.model.PresentationDefinitionDocument;
import com.izylife.ssi.repository.AdminClientRepository;
import com.izylife.ssi.repository.PresentationDefinitionRepository;
import com.izylife.ssi.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class AdminPresentationDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(AdminPresentationDefinitionService.class);

    private final PresentationDefinitionRepository definitionRepository;
    private final AdminClientRepository clientRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public AdminPresentationDefinitionService(PresentationDefinitionRepository definitionRepository,
                                              AdminClientRepository clientRepository,
                                              TenantRepository tenantRepository,
                                              ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.clientRepository = clientRepository;
        this.tenantRepository = tenantRepository;
        this.objectMapper = objectMapper;
    }

    public List<PresentationDefinitionResponse> listDefinitions(String tenantId, String clientId) {
        AdminClient client = ensureClient(tenantId, clientId);
        return definitionRepository.findAllByTenantIdAndClientIdOrderByCreatedAtAsc(client.getTenantId(), client.getClientId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PresentationDefinitionResponse createDefinition(String tenantId, String clientId, PresentationDefinitionRequest request) {
        AdminClient client = ensureClient(tenantId, clientId);
        String definitionId = normalizeDefinitionId(request.definitionId());
        if (definitionRepository.existsByTenantIdAndDefinitionId(client.getTenantId(), definitionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Definition ID already exists for this tenant");
        }

        JsonNode definitionNode = parseDefinition(request.definitionJson());
        validateDefinitionId(definitionId, definitionNode);

        PresentationDefinitionDocument document = new PresentationDefinitionDocument();
        document.setTenantId(client.getTenantId());
        document.setClientId(client.getClientId());
        document.setDefinitionId(definitionId);
        document.setName(definitionNode.path("name").asText(null));
        document.setDescription(definitionNode.path("purpose").asText(null));
        document.setDefinitionJson(writePrettyJson(definitionNode));
        Instant now = Instant.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        PresentationDefinitionDocument saved = definitionRepository.save(document);
        return toResponse(saved);
    }

    public PresentationDefinitionResponse updateDefinition(String tenantId, String clientId, String definitionId, PresentationDefinitionRequest request) {
        AdminClient client = ensureClient(tenantId, clientId);
        PresentationDefinitionDocument document = definitionRepository
                .findByTenantIdAndClientIdAndDefinitionId(client.getTenantId(), client.getClientId(), definitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Definition not found"));

        JsonNode definitionNode = parseDefinition(request.definitionJson());
        validateDefinitionId(definitionId, definitionNode);

        document.setName(definitionNode.path("name").asText(null));
        document.setDescription(definitionNode.path("purpose").asText(null));
        document.setDefinitionJson(writePrettyJson(definitionNode));
        document.setUpdatedAt(Instant.now());

        PresentationDefinitionDocument saved = definitionRepository.save(document);
        return toResponse(saved);
    }

    public PresentationDefinitionResponse getDefinition(String tenantId, String clientId, String definitionId) {
        AdminClient client = ensureClient(tenantId, clientId);
        PresentationDefinitionDocument document = definitionRepository
                .findByTenantIdAndClientIdAndDefinitionId(client.getTenantId(), client.getClientId(), definitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Definition not found"));
        return toResponse(document);
    }

    public void deleteDefinition(String tenantId, String clientId, String definitionId) {
        AdminClient client = ensureClient(tenantId, clientId);
        PresentationDefinitionDocument document = definitionRepository
                .findByTenantIdAndClientIdAndDefinitionId(client.getTenantId(), client.getClientId(), definitionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Definition not found"));
        definitionRepository.delete(document);
    }

    public PresentationDefinitionDocument getDocumentByDefinitionId(String definitionId) {
        return definitionRepository.findByDefinitionId(definitionId).orElse(null);
    }

    private AdminClient ensureClient(String tenantId, String clientId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        String normalizedClientId = clientId != null ? clientId.trim() : null;
        if (!StringUtils.hasText(normalizedClientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client ID is required");
        }
        return clientRepository.findByTenantIdAndClientId(tenantId, normalizedClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }

    private JsonNode parseDefinition(String json) {
        if (!StringUtils.hasText(json)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Definition JSON is required");
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            log.debug("Invalid presentation definition JSON", ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid presentation definition JSON");
        }
    }

    private void validateDefinitionId(String expectedId, JsonNode definition) {
        String jsonId = definition.path("id").asText(null);
        if (!StringUtils.hasText(jsonId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Presentation definition must include an 'id' field");
        }
        if (!jsonId.equals(expectedId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Definition ID mismatch between payload and JSON content");
        }
    }

    private String writePrettyJson(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialise presentation definition");
        }
    }

    private String normalizeDefinitionId(String definitionId) {
        if (!StringUtils.hasText(definitionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Definition ID is required");
        }
        return definitionId.trim();
    }

    private PresentationDefinitionResponse toResponse(PresentationDefinitionDocument document) {
        return new PresentationDefinitionResponse(
                document.getId(),
                document.getTenantId(),
                document.getClientId(),
                document.getDefinitionId(),
                document.getName(),
                document.getDescription(),
                document.getDefinitionJson(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
