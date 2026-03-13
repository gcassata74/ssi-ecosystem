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

import com.izylife.ssi.dto.admin.ClientResponse;
import com.izylife.ssi.dto.admin.ClientSecretResponse;
import com.izylife.ssi.dto.admin.CreateClientRequest;
import com.izylife.ssi.dto.admin.UpdateClientRequest;
import com.izylife.ssi.model.AdminClient;
import com.izylife.ssi.model.Tenant;
import com.izylife.ssi.repository.AdminClientRepository;
import com.izylife.ssi.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminClientService {

    private static final int SECRET_NUM_BYTES = 32;

    private final AdminClientRepository clientRepository;
    private final TenantRepository tenantRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminClientService(AdminClientRepository clientRepository, TenantRepository tenantRepository) {
        this.clientRepository = clientRepository;
        this.tenantRepository = tenantRepository;
    }

    public List<ClientResponse> listClients(String tenantId) {
        ensureTenantExists(tenantId);
        return clientRepository.findAllByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ClientSecretResponse createClient(String tenantId, CreateClientRequest request) {
        Tenant tenant = ensureTenantExists(tenantId);
        String clientId = normalizeClientId(request.clientId());
        if (clientRepository.existsByClientIdIgnoreCase(clientId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Client ID already exists");
        }

        AdminClient client = new AdminClient();
        client.setTenantId(tenant.getId());
        client.setClientId(clientId);
        client.setName(request.name().trim());
        client.setDescription(normalizeOptional(request.description()));
        client.setRedirectUris(normalizeRedirectUris(request.redirectUris()));
        Instant now = Instant.now();
        client.setCreatedAt(now);
        client.setUpdatedAt(now);

        String secret = generateClientSecret();
        client.setSecretHash(passwordEncoder.encode(secret));

        clientRepository.save(client);
        return new ClientSecretResponse(client.getClientId(), secret);
    }

    public ClientResponse updateClient(String tenantId, String clientId, UpdateClientRequest request) {
        AdminClient client = findClientOrThrow(tenantId, clientId);
        client.setName(request.name().trim());
        client.setDescription(normalizeOptional(request.description()));
        client.setRedirectUris(normalizeRedirectUris(request.redirectUris()));
        client.setUpdatedAt(Instant.now());
        AdminClient saved = clientRepository.save(client);
        return toResponse(saved);
    }

    public ClientSecretResponse rotateSecret(String tenantId, String clientId) {
        AdminClient client = findClientOrThrow(tenantId, clientId);
        String secret = generateClientSecret();
        client.setSecretHash(passwordEncoder.encode(secret));
        client.setUpdatedAt(Instant.now());
        clientRepository.save(client);
        return new ClientSecretResponse(client.getClientId(), secret);
    }

    public void deleteClient(String tenantId, String clientId) {
        AdminClient client = findClientOrThrow(tenantId, clientId);
        clientRepository.delete(client);
    }

    private AdminClient findClientOrThrow(String tenantId, String clientId) {
        String normalizedClientId = normalizeClientId(clientId);
        return clientRepository.findByTenantIdAndClientId(tenantId, normalizedClientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }

    private Tenant ensureTenantExists(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }

    private String normalizeClientId(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Client ID is required");
        }
        return clientId.trim();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeRedirectUris(List<String> redirectUris) {
        if (CollectionUtils.isEmpty(redirectUris)) {
            return List.of();
        }
        return redirectUris.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private ClientResponse toResponse(AdminClient client) {
        return new ClientResponse(
                client.getId(),
                client.getTenantId(),
                client.getClientId(),
                client.getName(),
                client.getDescription(),
                Objects.requireNonNullElse(client.getRedirectUris(), List.of()),
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }

    private String generateClientSecret() {
        byte[] bytes = new byte[SECRET_NUM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
