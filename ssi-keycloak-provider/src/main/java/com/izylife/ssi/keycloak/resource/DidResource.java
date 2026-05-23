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
import com.izylife.ssi.keycloak.model.RealmDidResponse;
import com.izylife.ssi.keycloak.util.DidKeyUtil;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Map;

public class DidResource {

    public static final String ATTR_DID = "ssi.did";
    public static final String ATTR_PUBLIC_JWK = "ssi.did.public-jwk";
    public static final String ATTR_PRIVATE_JWK = "ssi.did.private-jwk";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KeycloakSession session;
    private final RealmModel realm;

    public DidResource(KeycloakSession session, RealmModel realm) {
        this.session = session;
        this.realm = realm;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response regenerateDid() {
        requireAdmin();
        // Clear existing so ensureDid() always creates a fresh one
        realm.removeAttribute(ATTR_DID);
        realm.removeAttribute(ATTR_PUBLIC_JWK);
        realm.removeAttribute(ATTR_PRIVATE_JWK);
        return Response.ok(ensureDid()).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDid() {
        requireAdmin();
        return Response.ok(ensureDid()).build();
    }

    @GET
    @Path("private-jwk")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPrivateJwk() {
        requireAdmin();
        ensureDid();
        String privateJwkJson = realm.getAttribute(ATTR_PRIVATE_JWK);
        if (privateJwkJson == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try {
            Map<String, Object> jwk = MAPPER.readValue(privateJwkJson, new TypeReference<>() {});
            return Response.ok(jwk).build();
        } catch (Exception e) {
            return Response.serverError().build();
        }
    }

    @GET
    @Path("jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJwks() {
        String publicJwkJson = realm.getAttribute(ATTR_PUBLIC_JWK);
        if (publicJwkJson == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"keys\":[]}")
                    .build();
        }

        try {
            Map<String, Object> jwk = MAPPER.readValue(publicJwkJson, new TypeReference<>() {});
            return Response.ok("{\"keys\":[" + publicJwkJson + "]}").build();
        } catch (Exception e) {
            return Response.serverError().build();
        }
    }

    RealmDidResponse ensureDid() {
        String did = realm.getAttribute(ATTR_DID);
        if (did != null) {
            try {
                String publicJwkJson = realm.getAttribute(ATTR_PUBLIC_JWK);
                Map<String, Object> publicJwk = MAPPER.readValue(publicJwkJson, new TypeReference<>() {});
                return new RealmDidResponse(did, publicJwk);
            } catch (Exception ignored) {}
        }
        // Auto-generate on first access
        KeyPair kp = DidKeyUtil.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) kp.getPublic();
        ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();
        String newDid = DidKeyUtil.deriveDid(pub);
        String publicJwkJson = DidKeyUtil.toPublicJwkJson(pub, newDid);
        String privateJwkJson = DidKeyUtil.toPrivateJwkJson(priv, pub, newDid);
        realm.setAttribute(ATTR_DID, newDid);
        realm.setAttribute(ATTR_PUBLIC_JWK, publicJwkJson);
        realm.setAttribute(ATTR_PRIVATE_JWK, privateJwkJson);
        try {
            Map<String, Object> publicJwk = MAPPER.readValue(publicJwkJson, new TypeReference<>() {});
            return new RealmDidResponse(newDid, publicJwk);
        } catch (Exception e) {
            return new RealmDidResponse(newDid, null);
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
