package com.izylife.ssi.spid;

public class SpidProvider {

    private String identifier;
    private String entityId;
    private String name;
    private String imageUrl;

    public SpidProvider() {
    }

    public SpidProvider(String identifier, String entityId, String name, String imageUrl) {
        this.identifier = identifier;
        this.entityId = entityId;
        this.name = name;
        this.imageUrl = imageUrl;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
