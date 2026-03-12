package com.izylife.ssi.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "presentation_definitions")
public class PresentationDefinitionDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String definitionId;

    @Indexed
    private String tenantId;

    @Indexed
    private String clientId;

    private String name;
    private String description;
    private String definitionJson;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
