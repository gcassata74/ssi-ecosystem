package com.izylife.ssi.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateClientRequest(
        @NotBlank(message = "Client ID is required")
        @Size(max = 128, message = "Client ID must be 128 characters or fewer")
        String clientId,
        @NotBlank(message = "Client name is required")
        @Size(max = 128, message = "Client name must be 128 characters or fewer")
        String name,
        @Size(max = 512, message = "Description must be 512 characters or fewer")
        String description,
        List<@Size(max = 512, message = "Redirect URIs must be 512 characters or fewer") String> redirectUris
) {
}
