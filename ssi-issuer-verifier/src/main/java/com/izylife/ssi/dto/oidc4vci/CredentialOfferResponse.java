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

package com.izylife.ssi.dto.oidc4vci;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class CredentialOfferResponse {

    @JsonProperty("credential_offer")
    private Map<String, Object> credentialOffer = new HashMap<>();

    public CredentialOfferResponse(Map<String, Object> credentialOffer) {
        this.credentialOffer = credentialOffer;
    }

    public CredentialOfferResponse(String credentialIssuer,
                                   List<String> credentialConfigurationIds,
                                   String issuerState,
                                   String preAuthorizedCode,
                                   boolean userPinRequired) {
        Map<String, Object> offer = new HashMap<>();
        offer.put("credential_issuer", credentialIssuer);
        offer.put("credentials", new ArrayList<>(credentialConfigurationIds));

        Map<String, Object> grants = new HashMap<>();
        Map<String, Object> authorizationCode = new HashMap<>();
        authorizationCode.put("issuer_state", issuerState);
        grants.put("authorization_code", authorizationCode);

        Map<String, Object> preAuthorized = new HashMap<>();
        preAuthorized.put("pre-authorized_code", preAuthorizedCode);
        if (userPinRequired) {
            preAuthorized.put("user_pin_required", true);
        }
        grants.put("urn:ietf:params:oauth:grant-type:pre-authorized_code", preAuthorized);

        offer.put("grants", grants);
        this.credentialOffer = offer;
    }
}
