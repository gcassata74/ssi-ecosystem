/*
 * SSI Keycloak Provider
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

package com.izylife.ssi.keycloak.resource;

import jakarta.enterprise.inject.Vetoed;
import jakarta.ws.rs.Path;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

@Vetoed
@Path("")
public class SsiRealmResource {

    private final KeycloakSession session;
    private final RealmModel realm;

    public SsiRealmResource(KeycloakSession session, RealmModel realm) {
        this.session = session;
        this.realm = realm;
    }

    @Path("did")
    public DidResource did() {
        return new DidResource(session, realm);
    }

    @Path("credential-config")
    public CredentialConfigResource credentialConfig() {
        return new CredentialConfigResource(session, realm);
    }

    @Path("presentation-definition")
    public PresentationDefinitionResource presentationDefinition() {
        return new PresentationDefinitionResource(realm);
    }

    @Path("ui")
    public AdminUiResource ui() {
        return new AdminUiResource(realm);
    }
}
