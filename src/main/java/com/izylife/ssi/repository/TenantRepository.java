package com.izylife.ssi.repository;

import com.izylife.ssi.model.Tenant;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TenantRepository extends MongoRepository<Tenant, String> {
    Optional<Tenant> findByNameIgnoreCase(String name);
}
