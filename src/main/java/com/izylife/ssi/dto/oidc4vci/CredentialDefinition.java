package com.izylife.ssi.dto.oidc4vci;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CredentialDefinition {

    @JsonProperty("type")
    private List<String> type = new ArrayList<>();

    @JsonProperty("credentialSubject")
    private CredentialSubjectMetadata credentialSubject;

    public CredentialDefinition(List<String> type, CredentialSubjectMetadata credentialSubject) {
        this.type = type;
        this.credentialSubject = credentialSubject;
    }
}
