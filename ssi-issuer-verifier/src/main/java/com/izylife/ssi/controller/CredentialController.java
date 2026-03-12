package com.izylife.ssi.controller;

import com.izylife.ssi.dto.CredentialTemplateDto;
import com.izylife.ssi.dto.IssueCredentialRequest;
import com.izylife.ssi.dto.IssueCredentialResponse;
import com.izylife.ssi.service.CredentialService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/credentials", produces = MediaType.APPLICATION_JSON_VALUE)
public class CredentialController {

    private final CredentialService credentialService;

    public CredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping("/templates")
    public List<CredentialTemplateDto> getTemplates() {
        return credentialService.getTemplates();
    }

    @PostMapping(path = "/issue", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IssueCredentialResponse issueCredential(@Valid @RequestBody IssueCredentialRequest request) {
        return credentialService.issueCredential(request);
    }
}
