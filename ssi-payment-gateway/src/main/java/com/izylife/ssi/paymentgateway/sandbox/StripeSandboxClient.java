package com.izylife.ssi.paymentgateway.sandbox;

import com.izylife.ssi.paymentgateway.config.StripeSandboxProperties;
import com.izylife.ssi.paymentgateway.model.PaymentTransaction;
import com.izylife.ssi.paymentgateway.service.CredentialPaymentData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

@Component
public class StripeSandboxClient implements SandboxPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(StripeSandboxClient.class);

    private final StripeSandboxProperties properties;
    private final WebClient webClient;

    public StripeSandboxClient(StripeSandboxProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Override
    public SandboxPaymentResult authorize(PaymentTransaction transaction, CredentialPaymentData credentialData) {
        if (!isConfigured()) {
            log.warn("Stripe sandbox secret key missing. Returning simulated approval for demo purposes.");
            return SandboxPaymentResult.success("demo-" + transaction.getId(), "simulated");
        }
        long amountMinorUnits = toMinorUnits(transaction.getAmount());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(amountMinorUnits));
        form.add("currency", transaction.getCurrency().toLowerCase());
        form.add("payment_method", credentialData.paymentMethodId());
        form.add("description", transaction.getDescription() != null
                ? transaction.getDescription()
                : "SSI payment " + transaction.getId());
        form.add("metadata[holderDid]", credentialData.holderDid());
        form.add("metadata[paymentId]", transaction.getId());
        if (properties.isAutoConfirm()) {
            form.add("confirmation_method", "automatic");
            form.add("confirm", "true");
        }

        Mono<StripePaymentIntentResponse> responseMono = webClient.post()
                .uri("/v1/payment_intents")
                .header("Authorization", "Bearer " + properties.getSecretKey())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(StripePaymentIntentResponse.class);

        StripePaymentIntentResponse response = responseMono.block(Duration.ofSeconds(10));
        log.info("Stripe sandbox intent {} status {}", response.id(), response.status());
        if ("succeeded".equalsIgnoreCase(response.status()) || "requires_capture".equalsIgnoreCase(response.status())) {
            return SandboxPaymentResult.success(response.id(), response.status());
        }
        return SandboxPaymentResult.failure("Stripe intent status " + response.status());
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();
    }

    private boolean isConfigured() {
        String key = properties.getSecretKey();
        return key != null && !key.isBlank() && !key.contains("change");
    }

    private record StripePaymentIntentResponse(String id, String status, long amount, String currency) {
    }
}
