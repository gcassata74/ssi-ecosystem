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
public class CredentialSubjectField {

    @JsonProperty("path")
    private List<String> path;

    @JsonProperty("optional")
    private Boolean optional;

    @JsonProperty("name")
    private String name;

    public CredentialSubjectField(List<String> path, boolean optional) {
        this.path = path;
        this.optional = optional;
    }

    public CredentialSubjectField(List<String> path, boolean optional, String name) {
        this.path = path;
        this.optional = optional;
        this.name = name;
    }
}
