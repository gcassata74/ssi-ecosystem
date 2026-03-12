package com.izylife.ssi.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "admin_clients")
public class AdminClient {

    @Id
    private String id;

    @Indexed(unique = true)
    private String clientId;

    @Indexed
    private String tenantId;

    private String name;
    private String description;
    private List<String> redirectUris = new ArrayList<>();
    private String secretHash;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
