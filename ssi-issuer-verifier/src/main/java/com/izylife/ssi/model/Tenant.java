package com.izylife.ssi.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "tenants")
@Getter
@Setter
public class Tenant {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String contactEmail;

    private String description;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public Tenant() {
    }

    public Tenant(String name, String contactEmail, String description) {
        this.name = name;
        this.contactEmail = contactEmail;
        this.description = description;
    }
}
