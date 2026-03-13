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

package com.izylife.ssi.controller;

import com.izylife.ssi.service.Oidc4VpRequestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/oidc4vp")
public class Oidc4VpRequestController {

    private final Oidc4VpRequestService requestService;

    public Oidc4VpRequestController(Oidc4VpRequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping(value = "/requests/{requestId}", produces = "application/oauth-authz-req+jwt")
    public ResponseEntity<String> getSignedRequestObject(@PathVariable("requestId") String requestId) {
        return requestService.getRequestObject(requestId)
                .map(body -> ResponseEntity.ok()
                        .contentType(MediaType.valueOf("application/oauth-authz-req+jwt"))
                        .body(body))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getJwks() {
        return requestService.getPublicJwkSet().toJSONObject();
    }
}
