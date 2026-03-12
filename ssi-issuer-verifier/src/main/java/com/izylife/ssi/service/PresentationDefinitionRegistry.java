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
