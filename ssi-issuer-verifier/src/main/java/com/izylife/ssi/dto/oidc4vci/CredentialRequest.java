package com.izylife.ssi.dto.oidc4vci;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CredentialRequest {

    @JsonProperty("format")
    private String format;

    @JsonProperty("credential_configuration_id")
    private String credentialConfigurationId;

    @JsonProperty("credential_definition")
    private CredentialDefinitionRequest credentialDefinition;

    @JsonProperty("proof")
    private ProofRequest proof;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CredentialDefinitionRequest {

        @JsonProperty("type")
        private List<String> type;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProofRequest {

        @JsonProperty("proof_type")
        private String proofType;

        @JsonProperty("jwt")
        private String jwt;
    }
}
