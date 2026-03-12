package com.izylife.ssi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties appProperties;

    public WebSocketConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var registration = registry.addEndpoint("/ws");

        List<String> allowedOrigins = appProperties.getCors().getAllowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            registration.setAllowedOriginPatterns("*");
            registration.setAllowedOrigins("*");
        } else {
            String[] origins = allowedOrigins.toArray(String[]::new);
            registration.setAllowedOriginPatterns(origins);
            registration.setAllowedOrigins(origins);
        }

        registration.withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
