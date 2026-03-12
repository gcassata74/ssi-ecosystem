package com.izylife.ssi.dto.oidc4vci;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType = "bearer";

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("c_nonce")
    private String cNonce;

    @JsonProperty("c_nonce_expires_in")
    private long cNonceExpiresIn;
}
