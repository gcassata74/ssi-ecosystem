package com.izylife.ssi.paymentgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sandbox.stripe")
public class StripeSandboxProperties {

    private String baseUrl = "https://api.stripe.com";

    private String secretKey = "sk_test_your_key";

    private String paymentMethodId = "pm_card_visa";

    private boolean autoConfirm = true;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(String paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public boolean isAutoConfirm() {
        return autoConfirm;
    }

    public void setAutoConfirm(boolean autoConfirm) {
        this.autoConfirm = autoConfirm;
    }
}
