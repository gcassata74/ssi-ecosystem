package com.izylife.ssi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class IssueCredentialRequest {
    @NotBlank
    private String templateId;

    @NotEmpty
    private Map<String, String> claims;

    @NotBlank
    private String subjectDid;
}
