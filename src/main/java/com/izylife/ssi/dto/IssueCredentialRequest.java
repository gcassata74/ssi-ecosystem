package com.izylife.ssi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public class IssueCredentialRequest {
    @NotBlank
    private String templateId;

    @NotEmpty
    private Map<String, String> claims;

    @NotBlank
    private String subjectDid;

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public Map<String, String> getClaims() {
        return claims;
    }

    public void setClaims(Map<String, String> claims) {
        this.claims = claims;
    }

    public String getSubjectDid() {
        return subjectDid;
    }

    public void setSubjectDid(String subjectDid) {
        this.subjectDid = subjectDid;
    }
}
