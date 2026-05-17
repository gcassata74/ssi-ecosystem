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

import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class SpidAttributeMapper {

    private static final List<String> FISCAL_NUMBER_KEYS = List.of("fiscalNumber", "fiscalnumber", "codicefiscale", "codiceFiscale");
    private static final List<String> GIVEN_NAME_KEYS = List.of("givenName", "name", "nome");
    private static final List<String> FAMILY_NAME_KEYS = List.of("familyName", "surname", "cognome");
    private static final List<String> EMAIL_KEYS = List.of("email", "mail");

    public Oidc4vciService.StaffProfile map(Authentication authentication) {
        if (!(authentication instanceof Saml2Authentication saml2Authentication)) {
            return null;
        }
      Object principalObj = saml2Authentication.getPrincipal();
        if (!(principalObj instanceof Saml2AuthenticatedPrincipal principal)) {
            return null;
        }
        String fiscalNumber = firstAttribute(principal, FISCAL_NUMBER_KEYS).map(this::normalizeFiscalCode).orElse(null);
        String givenName = firstAttribute(principal, GIVEN_NAME_KEYS).orElse(null);
        String familyName = firstAttribute(principal, FAMILY_NAME_KEYS).orElse(null);
        String email = firstAttribute(principal, EMAIL_KEYS).orElse(null);

        String subjectIdentifier = Optional.ofNullable(fiscalNumber)
                .map(this::sanitizeIdentifier)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> sanitizeIdentifier(principal.getName()));

        if (subjectIdentifier == null || subjectIdentifier.isBlank()) {
            subjectIdentifier = "anonymous";
        }

        String subjectDid = "did:spid:" + subjectIdentifier;
        String employeeNumber = Optional.ofNullable(fiscalNumber).orElse(subjectIdentifier);
        String resolvedGivenName = Optional.ofNullable(givenName).orElse(principal.getName());
        String resolvedFamilyName = Optional.ofNullable(familyName).orElse("SPID User");
        String resolvedEmail = Optional.ofNullable(email).orElse(subjectIdentifier + "@spid.local");

        return new Oidc4vciService.StaffProfile(
                subjectDid,
                resolvedFamilyName,
                resolvedGivenName,
                "SPID Verified Staff",
                employeeNumber,
                resolvedEmail
        );
    }

    private Optional<String> firstAttribute(Saml2AuthenticatedPrincipal principal, List<String> keys) {
        for (String key : keys) {
            Object direct = principal.getFirstAttribute(key);
            if (direct != null) {
                return Optional.of(direct.toString());
            }
            String match = findAttributeIgnoreCase(principal.getAttributes(), key);
            if (match != null) {
                return Optional.of(match);
            }
        }
        return Optional.empty();
    }

    private String findAttributeIgnoreCase(Map<String, List<Object>> attributes, String target) {
        for (Map.Entry<String, List<Object>> entry : attributes.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(target)) {
                List<Object> values = entry.getValue();
                if (values != null && !values.isEmpty() && values.get(0) != null) {
                    return values.get(0).toString();
                }
            }
        }
        return null;
    }

    private String normalizeFiscalCode(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        int separatorIndex = trimmed.indexOf('-');
        if (separatorIndex >= 0 && separatorIndex < trimmed.length() - 1) {
            trimmed = trimmed.substring(separatorIndex + 1);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String sanitizeIdentifier(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("[^A-Za-z0-9]", "");
    }
}
