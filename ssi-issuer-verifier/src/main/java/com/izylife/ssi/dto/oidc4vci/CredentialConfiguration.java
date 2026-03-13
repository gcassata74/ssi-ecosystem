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
public class CredentialConfiguration {

    @JsonProperty("format")
    private String format;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("cryptographic_binding_methods_supported")
    private List<String> cryptographicBindingMethodsSupported = new ArrayList<>();

    @JsonProperty("credential_signing_alg_values_supported")
    private List<String> credentialSigningAlgValuesSupported = new ArrayList<>();

    @JsonProperty("display")
    private List<DisplayEntry> display = new ArrayList<>();

    @JsonProperty("credential_definition")
    private CredentialDefinition credentialDefinition;

    @JsonProperty("proof_types_supported")
    private Map<String, ProofTypeMetadata> proofTypesSupported;
}
