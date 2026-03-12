package com.izylife.ssi.paymentgateway.service;

import com.izylife.ssi.paymentgateway.model.PaymentTransaction;

import java.net.URI;

public record PaymentCallbackResult(PaymentTransaction transaction, URI redirectUri) {
}
