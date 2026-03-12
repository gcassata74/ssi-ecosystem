package com.izylife.ssi.paymentgateway.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.izylife.ssi.paymentgateway.model.PaymentStatus;
import com.izylife.ssi.paymentgateway.model.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentView(
        String paymentId,
        BigDecimal amount,
        String currency,
        String description,
        String returnUrl,
        PaymentStatus status,
        Instant updatedAt,
        String authorizationUrl,
        String holderDid,
        String holderName,
        String paymentMethodId,
        String sandboxPaymentId,
        String failureReason,
        JsonNode credentialPreview
) {

    public static PaymentView from(PaymentTransaction tx) {
        return new PaymentView(
                tx.getId(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getDescription(),
                tx.getReturnUrl(),
                tx.getStatus(),
                tx.getUpdatedAt(),
                tx.getAuthorizationUrl(),
                tx.getHolderDid(),
                tx.getHolderName(),
                tx.getPaymentMethodId(),
                tx.getSandboxPaymentId(),
                tx.getFailureReason(),
                tx.getCredentialPreview()
        );
    }
}
