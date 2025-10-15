package com.izylife.ssi.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CredentialPreviewDto {

    private String issuerName;
    private String issuerId;
    private List<String> type;
    private Map<String, Object> subject;
    private String rawJson;
}
