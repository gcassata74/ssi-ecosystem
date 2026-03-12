package com.izylife.ssi.paymentgateway.model;

public enum PaymentStatus {
    CREATED,
    AUTHORIZATION_REQUIRED,
    CREDENTIAL_VERIFIED,
    SANDBOX_CONFIRMED,
    FAILED
}
