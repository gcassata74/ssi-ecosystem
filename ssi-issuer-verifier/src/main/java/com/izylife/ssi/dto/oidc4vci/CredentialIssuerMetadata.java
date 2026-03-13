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

package com.izylife.ssi.dto.oidc4vci;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CredentialIssuerMetadata {

    @JsonProperty("credential_issuer")
    private String credentialIssuer;

    @JsonProperty("credential_endpoint")
    private String credentialEndpoint;

    @JsonProperty("display")
    private List<DisplayEntry> display = new ArrayList<>();

    @JsonProperty("authorization_servers")
    private List<String> authorizationServers = new ArrayList<>();

    @JsonProperty("token_endpoint")
    private String tokenEndpoint;

    @JsonProperty("grant_types_supported")
    private List<String> grantTypesSupported = new ArrayList<>();

    @JsonProperty("credential_configurations_supported")
    private Map<String, CredentialConfiguration> credentialConfigurationsSupported;
}
