package com.izylife.ssi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyPresentationRequest {
    @JsonProperty("presentation_payload")
    @NotBlank
    private String presentationPayload;

    @JsonProperty("challenge")
    @NotBlank
    private String challenge;

    @JsonProperty("presentation_submission")
    private String presentationSubmission;

    @JsonProperty("state")
    private String state;
}
