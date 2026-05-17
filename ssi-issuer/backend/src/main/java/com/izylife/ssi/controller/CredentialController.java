/*
 * SSI Issuer Verifier
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
