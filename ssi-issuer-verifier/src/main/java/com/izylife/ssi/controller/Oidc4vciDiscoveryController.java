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

import com.izylife.ssi.dto.oidc4vci.AuthorizationServerMetadata;
import com.izylife.ssi.dto.oidc4vci.CredentialIssuerMetadata;
import com.izylife.ssi.service.Oidc4vciService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class Oidc4vciDiscoveryController {

    private final Oidc4vciService oidc4vciService;

    public Oidc4vciDiscoveryController(Oidc4vciService oidc4vciService) {
        this.oidc4vciService = oidc4vciService;
    }

    @GetMapping("/.well-known/openid-credential-issuer")
    public CredentialIssuerMetadata getCredentialIssuerMetadata() {
        return oidc4vciService.buildCredentialIssuerMetadata();
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public AuthorizationServerMetadata getAuthorizationServerMetadata() {
        return oidc4vciService.buildAuthorizationServerMetadata();
    }
}
