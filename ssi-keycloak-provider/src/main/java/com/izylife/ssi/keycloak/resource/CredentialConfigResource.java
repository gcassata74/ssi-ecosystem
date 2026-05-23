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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.keycloak.model.ClaimMapping;
import com.izylife.ssi.keycloak.model.CredentialConfigResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

import java.util.Arrays;
import java.util.List;

public class CredentialConfigResource {

    static final String ATTR_CLAIMS = "ssi.credential.claims";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<ClaimMapping> DEFAULT_CLAIMS = Arrays.asList(
            new ClaimMapping("given_name",     "givenName",      true),
            new ClaimMapping("family_name",    "familyName",     true),
            new ClaimMapping("email",          "email",          false),
            new ClaimMapping("codice_fiscale", "codiceFiscale",  true),
            new ClaimMapping("indirizzo",      "indirizzo",      true),
            new ClaimMapping("employee_id",    "employeeNumber", false),
            new ClaimMapping("job_title",      "role",           false)
    );

    private final KeycloakSession session;
    private final RealmModel realm;

    public CredentialConfigResource(KeycloakSession session, RealmModel realm) {
        this.session = session;
        this.realm = realm;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig() {
        requireAdmin();

        String claimsJson = realm.getAttribute(ATTR_CLAIMS);
        if (claimsJson == null) {
            return Response.ok(new CredentialConfigResponse(DEFAULT_CLAIMS)).build();
        }

        try {
            List<ClaimMapping> claims = MAPPER.readValue(claimsJson, new TypeReference<>() {});
            return Response.ok(new CredentialConfigResponse(claims)).build();
        } catch (Exception e) {
            return Response.serverError().entity("Failed to read credential config").build();
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateConfig(List<ClaimMapping> claims) {
        requireAdmin();

        if (claims == null || claims.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"claims list must not be empty\"}")
                    .build();
        }

        try {
            String json = MAPPER.writeValueAsString(claims);
            realm.setAttribute(ATTR_CLAIMS, json);
            return Response.ok(new CredentialConfigResponse(claims)).build();
        } catch (Exception e) {
            return Response.serverError().entity("Failed to save credential config").build();
        }
    }

    private void requireAdmin() {
        AuthenticationManager.AuthResult auth =
                new AppAuthManager.BearerTokenAuthenticator(session).authenticate();
        if (auth == null) {
            throw new jakarta.ws.rs.ForbiddenException("Admin access required");
        }
        // Master realm admin: "admin" role in realmAccess
        if (auth.getToken().getRealmAccess() != null
                && auth.getToken().getRealmAccess().isUserInRole("admin")) {
            return;
        }
        // Local realm admin: "realm-admin" role in realm-management resource access
        org.keycloak.representations.AccessToken.Access realmMgmt =
                auth.getToken().getResourceAccess("realm-management");
        if (realmMgmt != null && realmMgmt.isUserInRole("realm-admin")) {
            return;
        }
        throw new jakarta.ws.rs.ForbiddenException("Admin access required");
    }
}
