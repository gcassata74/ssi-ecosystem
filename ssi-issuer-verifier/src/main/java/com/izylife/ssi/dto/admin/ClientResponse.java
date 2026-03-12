package com.izylife.ssi.dto.admin;

import java.time.Instant;
import java.util.List;

public record ClientResponse(
        String id,
        String tenantId,
        String clientId,
        String name,
        String description,
        List<String> redirectUris,
        Instant createdAt,
        Instant updatedAt
) {
}
