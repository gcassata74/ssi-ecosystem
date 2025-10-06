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
public class CredentialSubjectMetadata {

    @JsonProperty("fields")
    private List<CredentialSubjectField> fields = new ArrayList<>();

    public CredentialSubjectMetadata(List<CredentialSubjectField> fields) {
        this.fields = fields;
    }
}
