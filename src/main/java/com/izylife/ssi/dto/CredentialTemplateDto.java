package com.izylife.ssi.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CredentialTemplateDto {
    private String id;
    private String name;
    private String description;
    private List<String> claims;

    public CredentialTemplateDto() {
    }

    public CredentialTemplateDto(String id, String name, String description, List<String> claims) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.claims = claims;
    }
}
