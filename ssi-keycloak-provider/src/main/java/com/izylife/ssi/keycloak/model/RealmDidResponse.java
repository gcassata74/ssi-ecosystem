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

public class RealmDidResponse {

    @JsonProperty("did")
    private String did;

    @JsonProperty("publicJwk")
    private Object publicJwk;

    public RealmDidResponse() {}

    public RealmDidResponse(String did, Object publicJwk) {
        this.did = did;
        this.publicJwk = publicJwk;
    }

    public String getDid() { return did; }
    public void setDid(String did) { this.did = did; }

    public Object getPublicJwk() { return publicJwk; }
    public void setPublicJwk(Object publicJwk) { this.publicJwk = publicJwk; }
}
