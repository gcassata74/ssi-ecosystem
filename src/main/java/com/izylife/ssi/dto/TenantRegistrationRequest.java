package com.izylife.ssi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TenantRegistrationRequest(
        @NotBlank(message = "Tenant name is required")
        @Size(max = 128, message = "Tenant name must be 128 characters or fewer")
        String name,

        @NotBlank(message = "Contact email is required")
        @Email(message = "Contact email must be a valid address")
        String contactEmail,

        @Size(max = 512, message = "Description must be 512 characters or fewer")
        String description
) {
}
