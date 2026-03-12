package com.izylife.ssi.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record PresentationDefinitionRequest(
        @NotBlank(message = "Definition ID is required")
        String definitionId,
        @NotBlank(message = "Presentation definition JSON is required")
        String definitionJson
) {
}
