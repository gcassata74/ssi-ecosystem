/*
 * SSI Issuer
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.izylife.ssi.config;

import com.izylife.ssi.config.AppProperties.SpidProperties;
import com.izylife.ssi.security.SpidAuthenticationSuccessHandler;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2AuthenticationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.text.ParseException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AppProperties appProperties,
                                                   ObjectProvider<SpidAuthenticationSuccessHandler> successHandlerProvider,
                                                   ObjectProvider<Saml2AuthenticationRequestResolver> authnRequestResolverProvider,
                                                   ObjectProvider<JwtDecoder> jwtDecoderProvider) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/onboarding/issuer/enroll").authenticated()
                        .anyRequest().permitAll()
                );

        JwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();
        if (jwtDecoder != null) {
            http.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.decoder(jwtDecoder)));
        }

        SpidProperties spid = appProperties.getSpid();
        if (spid != null && spid.isEnabled()) {
            SpidAuthenticationSuccessHandler successHandler = successHandlerProvider.getIfAvailable();
            Saml2AuthenticationRequestResolver authnRequestResolver = authnRequestResolverProvider.getIfAvailable();
            http.saml2Login(saml -> {
                if (successHandler != null) {
                    saml.successHandler(successHandler);
                }
                if (authnRequestResolver != null) {
                    saml.authenticationRequestResolver(authnRequestResolver);
                }
            });
            String logoutTarget = Optional.ofNullable(spid.getPostLoginRedirect()).orElse("/issuer");
            http.logout(logout -> logout.logoutSuccessUrl(logoutTarget));
        }

        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.keycloak.oidc-enabled", havingValue = "true")
    public JwtDecoder keycloakJwtDecoder(AppProperties appProperties) {
        String base = appProperties.getKeycloak().getBaseUrl().replaceAll("/+$", "");
        ConcurrentHashMap<String, NimbusJwtDecoder> cache = new ConcurrentHashMap<>();
        return tokenValue -> {
            try {
                JWT raw = JWTParser.parse(tokenValue);
                String iss = (String) raw.getJWTClaimsSet().getClaim("iss");
                // iss = "http://<host>/realms/<realm>"
                String realm = iss.substring(iss.lastIndexOf('/') + 1);
                NimbusJwtDecoder decoder = cache.computeIfAbsent(realm, r ->
                        NimbusJwtDecoder.withJwkSetUri(base + "/realms/" + r + "/protocol/openid-connect/certs").build()
                );
                return decoder.decode(tokenValue);
            } catch (ParseException e) {
                throw new JwtException("Failed to parse JWT: " + e.getMessage(), e);
            }
        };
    }
}
