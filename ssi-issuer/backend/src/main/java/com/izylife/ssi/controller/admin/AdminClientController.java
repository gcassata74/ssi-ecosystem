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

package com.izylife.ssi.controller.admin;

import com.izylife.ssi.dto.admin.ClientResponse;
import com.izylife.ssi.dto.admin.ClientSecretResponse;
import com.izylife.ssi.dto.admin.CreateClientRequest;
import com.izylife.ssi.dto.admin.UpdateClientRequest;
import com.izylife.ssi.service.AdminClientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/admin/tenants/{tenantId}/clients", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminClientController {

    private final AdminClientService adminClientService;

    public AdminClientController(AdminClientService adminClientService) {
        this.adminClientService = adminClientService;
    }

    @GetMapping
    public List<ClientResponse> listClients(@PathVariable String tenantId) {
        return adminClientService.listClients(tenantId);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ClientSecretResponse createClient(@PathVariable String tenantId,
                                             @Valid @RequestBody CreateClientRequest request) {
        return adminClientService.createClient(tenantId, request);
    }

    @PutMapping(path = "/{clientId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ClientResponse updateClient(@PathVariable String tenantId,
                                       @PathVariable String clientId,
                                       @Valid @RequestBody UpdateClientRequest request) {
        return adminClientService.updateClient(tenantId, clientId, request);
    }

    @PostMapping(path = "/{clientId}/rotate-secret")
    public ClientSecretResponse rotateSecret(@PathVariable String tenantId,
                                             @PathVariable String clientId) {
        return adminClientService.rotateSecret(tenantId, clientId);
    }

    @DeleteMapping(path = "/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClient(@PathVariable String tenantId,
                             @PathVariable String clientId) {
        adminClientService.deleteClient(tenantId, clientId);
    }
}
