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
