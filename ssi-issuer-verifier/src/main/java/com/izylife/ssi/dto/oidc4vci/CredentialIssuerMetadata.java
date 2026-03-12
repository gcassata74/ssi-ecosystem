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
