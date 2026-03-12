package com.izylife.ssi.paymentgateway.sandbox;

import com.izylife.ssi.paymentgateway.model.PaymentTransaction;
import com.izylife.ssi.paymentgateway.service.CredentialPaymentData;

public interface SandboxPaymentClient {

    SandboxPaymentResult authorize(PaymentTransaction transaction, CredentialPaymentData credentialData);
}
