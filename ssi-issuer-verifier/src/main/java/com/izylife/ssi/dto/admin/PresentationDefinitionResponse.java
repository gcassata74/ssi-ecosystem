package com.izylife.ssi.dto.admin;

import java.time.Instant;

public record PresentationDefinitionResponse(
        String id,
        String tenantId,
        String clientId,
        String definitionId,
        String name,
        String description,
        String definitionJson,
        Instant createdAt,
        Instant updatedAt
) {
}
