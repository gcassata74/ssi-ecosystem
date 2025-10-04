package com.izylife.ssi.model;

import lombok.Getter;

import java.util.List;

@Getter
public class CredentialTemplate {
    private String id;
    private String name;
    private String description;
    private List<String> claims;

    public CredentialTemplate(String id, String name, String description, List<String> claims) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.claims = claims;
    }
}
