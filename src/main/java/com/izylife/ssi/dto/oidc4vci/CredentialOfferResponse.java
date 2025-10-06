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
