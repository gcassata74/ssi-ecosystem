package com.izylife.ssi.paymentgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "ssi.payment-gateway")
public class GatewayProperties {

    private URI publicBaseUrl = URI.create("http://localhost:9092");

    private URI defaultReturnUrl = URI.create("http://localhost:9092/demo/result");

    public URI getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(URI publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public URI getDefaultReturnUrl() {
        return defaultReturnUrl;
    }

    public void setDefaultReturnUrl(URI defaultReturnUrl) {
        this.defaultReturnUrl = defaultReturnUrl;
    }
}
