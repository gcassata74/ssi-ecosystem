package com.izylife.ssi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Oidc4VpSubmission {

    @JsonProperty("vp_token")
    private String vpToken;

    @JsonProperty("presentation_submission")
    private String presentationSubmission;

    @JsonProperty("state")
    private String state;

}
