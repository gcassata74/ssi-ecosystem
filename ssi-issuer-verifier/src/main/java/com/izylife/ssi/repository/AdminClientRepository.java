package com.izylife.ssi.repository;

import com.izylife.ssi.model.AdminClient;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AdminClientRepository extends MongoRepository<AdminClient, String> {
    Optional<AdminClient> findByTenantIdAndClientId(String tenantId, String clientId);
    List<AdminClient> findAllByTenantIdOrderByCreatedAtAsc(String tenantId);
    boolean existsByClientIdIgnoreCase(String clientId);
}
