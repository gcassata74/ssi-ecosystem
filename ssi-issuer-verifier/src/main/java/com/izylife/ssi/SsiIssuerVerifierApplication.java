package com.izylife.ssi;

import com.izylife.ssi.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class SsiIssuerVerifierApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsiIssuerVerifierApplication.class, args);
    }
}
