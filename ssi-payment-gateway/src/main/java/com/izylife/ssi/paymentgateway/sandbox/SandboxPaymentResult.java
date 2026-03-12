package com.izylife.ssi.paymentgateway.sandbox;

public record SandboxPaymentResult(boolean success, String paymentId, String message) {

    public static SandboxPaymentResult success(String paymentId, String message) {
        return new SandboxPaymentResult(true, paymentId, message);
    }

    public static SandboxPaymentResult failure(String message) {
        return new SandboxPaymentResult(false, null, message);
    }
}
