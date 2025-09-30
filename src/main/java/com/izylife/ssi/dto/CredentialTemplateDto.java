package com.izylife.ssi.dto;

import java.util.List;

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getClaims() {
        return claims;
    }

    public void setClaims(List<String> claims) {
        this.claims = claims;
    }
}
