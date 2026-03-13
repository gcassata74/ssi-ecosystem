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

package com.izylife.ssi.service;

import com.izylife.ssi.dto.CredentialTemplateDto;
import com.izylife.ssi.dto.IssueCredentialRequest;
import com.izylife.ssi.dto.IssueCredentialResponse;
import com.izylife.ssi.model.CredentialTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CredentialService {

    private final List<CredentialTemplate> templates;
    private final OnboardingStateService onboardingStateService;

    public CredentialService(OnboardingStateService onboardingStateService) {
        this.onboardingStateService = onboardingStateService;
        this.templates = List.of(
                new CredentialTemplate(
                        "pa-staff",
                        "Public Authority Staff ID",
                        "Authorizes Izylife operators to act on behalf of the public authority.",
                        List.of("familyName", "givenName", "employeeNumber", "role")
                ),
                new CredentialTemplate(
                        "pa-license",
                        "Public Authority License",
                        "Attests that the holder owns a valid Izylife operating license.",
                        List.of("licenseId", "validUntil", "jurisdiction")
                )
        );
    }

    public List<CredentialTemplateDto> getTemplates() {
        return templates.stream()
                .map(template -> new CredentialTemplateDto(
                        template.getId(),
                        template.getName(),
                        template.getDescription(),
                        template.getClaims()))
                .collect(Collectors.toList());
    }

    public IssueCredentialResponse issueCredential(IssueCredentialRequest request) {
        Map<String, String> payload = Map.of(
                "credentialId", UUID.randomUUID().toString(),
                "templateId", request.getTemplateId(),
                "subjectDid", request.getSubjectDid(),
                "claims", request.getClaims().toString()
        );

        String encodedPayload = Base64.getEncoder().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String qrCodePayload = "SSICredential:" + encodedPayload;

        onboardingStateService.showVerifierQr();
        return new IssueCredentialResponse(
                payload.get("credentialId"),
                encodedPayload,
                qrCodePayload
        );
    }
}
