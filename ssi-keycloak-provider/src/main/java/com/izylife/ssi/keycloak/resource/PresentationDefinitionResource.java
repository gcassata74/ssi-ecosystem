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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.RealmModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and exposes an OID4VP Presentation Definition derived from the realm's
 * configured claim mappings.  Public endpoint — no admin token required.
 *
 * GET /realms/{realm}/ssi-issuer/presentation-definition
 */
public class PresentationDefinitionResource {

    private static final String CREDENTIAL_TYPE = "PublicAuthorityStaffCredential";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<ClaimMapping> DEFAULT_CLAIMS = Arrays.asList(
            new ClaimMapping("given_name",  "givenName",      true),
            new ClaimMapping("family_name", "familyName",     true),
            new ClaimMapping("email",       "email",          false),
            new ClaimMapping("employee_id", "employeeNumber", false),
            new ClaimMapping("job_title",   "role",           false)
    );

    private final RealmModel realm;

    public PresentationDefinitionResource(RealmModel realm) {
        this.realm = realm;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPresentationDefinition() {
        List<ClaimMapping> claims = loadClaims();
        Map<String, Object> definition = buildDefinition(realm.getName(), claims);
        return Response.ok(definition).build();
    }

    private List<ClaimMapping> loadClaims() {
        String json = realm.getAttribute(CredentialConfigResource.ATTR_CLAIMS);
        if (json == null || json.isBlank()) {
            return DEFAULT_CLAIMS;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return DEFAULT_CLAIMS;
        }
    }

    private Map<String, Object> buildDefinition(String realmName, List<ClaimMapping> claims) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("id", "staff-credential");
        definition.put("name", "Credenziale personale — realm " + realmName);
        definition.put("purpose", "Autenticazione personale tramite staff credential del realm " + realmName + ".");

        // Format: jwt_vc_json / ES256 (matches what the issuer emits)
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("jwt_vc_json", Map.of("alg", List.of("ES256")));
        format.put("jwt_vp_json", Map.of("alg", List.of("ES256")));
        definition.put("format", format);

        // input_descriptors
        List<Map<String, Object>> descriptors = new ArrayList<>();
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("id", "staff");
        descriptor.put("name", "Staff credential del realm " + realmName);
        descriptor.put("purpose", "Verifica l'appartenenza al personale del realm " + realmName + ".");

        List<Map<String, Object>> fields = new ArrayList<>();

        // Always require the credential type
        fields.add(typeConstraint());

        // One field constraint per claim mapping
        for (ClaimMapping mapping : claims) {
            fields.add(buildFieldConstraint(mapping));
        }

        descriptor.put("constraints", Map.of("fields", fields));
        descriptors.add(descriptor);
        definition.put("input_descriptors", descriptors);

        return definition;
    }

    private Map<String, Object> typeConstraint() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("path", List.of("$.type", "$.vc.type"));
        field.put("filter", Map.of(
                "type", "array",
                "contains", Map.of("const", CREDENTIAL_TYPE)
        ));
        return field;
    }

    private Map<String, Object> buildFieldConstraint(ClaimMapping mapping) {
        String claim = mapping.getCredentialClaim();
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("path", List.of(
                "$.credentialSubject." + claim,
                "$.vc.credentialSubject." + claim
        ));
        field.put("purpose", "Campo " + claim + " della credential.");
        if (mapping.isMandatory()) {
            field.put("filter", Map.of("type", "string", "minLength", 1));
        } else {
            field.put("optional", true);
        }
        return field;
    }
}
