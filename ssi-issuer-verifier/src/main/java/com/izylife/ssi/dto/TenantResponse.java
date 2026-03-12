package com.izylife.ssi.dto;

import java.time.Instant;

public record TenantResponse(
        String id,
        String name,
        String contactEmail,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
