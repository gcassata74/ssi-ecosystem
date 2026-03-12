package com.izylife.ssi.repository;

import com.izylife.ssi.model.PresentationDefinitionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PresentationDefinitionRepository extends MongoRepository<PresentationDefinitionDocument, String> {
    Optional<PresentationDefinitionDocument> findByDefinitionId(String definitionId);
    List<PresentationDefinitionDocument> findAllByTenantIdAndClientIdOrderByCreatedAtAsc(String tenantId, String clientId);
    Optional<PresentationDefinitionDocument> findByTenantIdAndClientIdAndDefinitionId(String tenantId, String clientId, String definitionId);
    boolean existsByTenantIdAndDefinitionId(String tenantId, String definitionId);
}
