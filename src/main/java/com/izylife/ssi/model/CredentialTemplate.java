package com.izylife.ssi.model;

import java.util.List;

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

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getClaims() {
        return claims;
    }
}
