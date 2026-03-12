package com.izylife.ssi.paymentgateway.service;

import com.izylife.ssi.paymentgateway.config.IssuerClientProperties;
import com.izylife.ssi.paymentgateway.web.dto.TokenResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class OidcClient {

    private final WebClient webClient;
    private final IssuerClientProperties properties;

    public OidcClient(WebClient.Builder builder, IssuerClientProperties properties) {
        this.properties = properties;
        this.webClient = builder
                .baseUrl(properties.resolveTokenEndpoint().toString())
                .build();
    }

    public TokenResponse exchangeAuthorizationCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.getClientId());
        if (properties.getClientSecret() != null && !properties.getClientSecret().isBlank()) {
            form.add("client_secret", properties.getClientSecret());
        }
        form.add("redirect_uri", properties.getRedirectUri().toString());
        form.add("code", code);
        form.add("code_verifier", codeVerifier);

        Mono<TokenResponse> responseMono = webClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class);

        return responseMono.block(Duration.ofSeconds(10));
    }
}
