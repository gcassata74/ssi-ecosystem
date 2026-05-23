/*
 * SSI Issuer
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

import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.service.KeycloakRealmConfigService;
import com.izylife.ssi.service.KeycloakRealmConfigService.ClaimMapping;
import com.izylife.ssi.service.KeycloakRealmConfigService.RealmDidInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/admin/realm-ssi", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(name = "app.keycloak.oidc-enabled", havingValue = "true")
public class AdminRealmSsiController {

    private final KeycloakRealmConfigService realmConfigService;
    private final AppProperties appProperties;

    public AdminRealmSsiController(KeycloakRealmConfigService realmConfigService,
                                    AppProperties appProperties) {
        this.realmConfigService = realmConfigService;
        this.appProperties = appProperties;
    }

    @GetMapping("/did")
    public ResponseEntity<RealmDidInfo> getDid() {
        try {
            return ResponseEntity.ok(realmConfigService.getRealmDid(configuredRealm()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/did")
    public ResponseEntity<RealmDidInfo> generateDid() {
        return ResponseEntity.ok(realmConfigService.generateDid(configuredRealm()));
    }

    @GetMapping("/credential-config")
    public ResponseEntity<List<ClaimMapping>> getCredentialConfig() {
        return ResponseEntity.ok(realmConfigService.getClaimConfig(configuredRealm()));
    }

    @PutMapping(path = "/credential-config", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ClaimMapping>> updateCredentialConfig(@RequestBody List<ClaimMapping> claims) {
        realmConfigService.updateClaimConfig(configuredRealm(), claims);
        return ResponseEntity.ok(claims);
    }

    private String configuredRealm() {
        String realm = appProperties.getKeycloak().getRealm();
        return (realm != null && !realm.isBlank()) ? realm : "master";
    }
}
