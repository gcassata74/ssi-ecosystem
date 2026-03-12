package com.izylife.ssi.paymentgateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CredentialPreviewParser {

    private final ObjectMapper objectMapper;

    public CredentialPreviewParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CredentialPaymentData toPaymentData(Map<String, Object> preview) {
        if (preview == null) {
            throw new IllegalStateException("credential_preview missing from token response");
        }
        JsonNode root = objectMapper.valueToTree(preview);
        JsonNode subject = root.path("credentialSubject");
        String holderDid = subject.path("id").asText(null);
        if (holderDid == null || holderDid.isBlank()) {
            holderDid = root.path("sub").asText(null);
        }
        String holderName = buildHolderName(subject);
        String paymentMethodId = subject.path("paymentMethodId").asText(null);
        if (paymentMethodId == null || paymentMethodId.isBlank()) {
            paymentMethodId = subject.path("paymentInstrumentId").asText(null);
        }
        if (paymentMethodId == null || paymentMethodId.isBlank()) {
            throw new IllegalStateException("paymentMethodId missing from credential");
        }
        return new CredentialPaymentData(holderDid, holderName, paymentMethodId, root);
    }

    private String buildHolderName(JsonNode subject) {
        String given = subject.path("givenName").asText("");
        String family = subject.path("familyName").asText("");
        String full = (given + " " + family).trim();
        if (!full.isBlank()) {
            return full;
        }
        return subject.path("legalName").asText(null);
    }
}
