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

import com.izylife.ssi.dto.TenantRegistrationRequest;
import com.izylife.ssi.dto.TenantResponse;
import com.izylife.ssi.model.Tenant;
import com.izylife.ssi.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public TenantResponse registerTenant(TenantRegistrationRequest request) {
        String normalisedName = normalise(request.name());
        tenantRepository.findByNameIgnoreCase(normalisedName)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A tenant with this name already exists");
                });

        Tenant tenant = new Tenant();
        tenant.setName(normalisedName);
        tenant.setContactEmail(request.contactEmail().trim());
        tenant.setDescription(normaliseOptional(request.description()));
        Instant now = Instant.now();
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);

        Tenant saved = tenantRepository.save(tenant);
        return toResponse(saved);
    }

    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .sorted(Comparator.comparing(Tenant::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList();
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getContactEmail(),
                tenant.getDescription(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }

    private String normalise(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant name is required");
        }
        return value.trim();
    }

    private String normaliseOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
