package com.izylife.ssi.paymentgateway.service;

import com.fasterxml.jackson.databind.JsonNode;

public record CredentialPaymentData(
        String holderDid,
        String holderName,
        String paymentMethodId,
        JsonNode credentialPreview
) {
}
