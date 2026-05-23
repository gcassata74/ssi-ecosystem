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

package com.izylife.ssi.keycloak.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ClaimMapping {

    @JsonProperty("keycloakClaim")
    private String keycloakClaim;

    @JsonProperty("credentialClaim")
    private String credentialClaim;

    @JsonProperty("mandatory")
    private boolean mandatory;

    public ClaimMapping() {}

    public ClaimMapping(String keycloakClaim, String credentialClaim, boolean mandatory) {
        this.keycloakClaim = keycloakClaim;
        this.credentialClaim = credentialClaim;
        this.mandatory = mandatory;
    }

    public String getKeycloakClaim() { return keycloakClaim; }
    public void setKeycloakClaim(String keycloakClaim) { this.keycloakClaim = keycloakClaim; }

    public String getCredentialClaim() { return credentialClaim; }
    public void setCredentialClaim(String credentialClaim) { this.credentialClaim = credentialClaim; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
}
