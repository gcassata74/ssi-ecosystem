package com.izylife.ssi.paymentgateway;

import com.izylife.ssi.paymentgateway.config.GatewayProperties;
import com.izylife.ssi.paymentgateway.config.IssuerClientProperties;
import com.izylife.ssi.paymentgateway.config.StripeSandboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties({
        IssuerClientProperties.class,
        GatewayProperties.class,
        StripeSandboxProperties.class
})
@SpringBootApplication
public class SsiPaymentGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsiPaymentGatewayApplication.class, args);
    }
}
