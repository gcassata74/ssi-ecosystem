/*
 * SSI Issuer Verifier
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2AuthenticationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Optional;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                  AppProperties appProperties,
                                                  ObjectProvider<SpidAuthenticationSuccessHandler> successHandlerProvider,
                                                  ObjectProvider<Saml2AuthenticationRequestResolver> authnRequestResolverProvider) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

        SpidProperties spid = appProperties.getSpid();
        if (spid != null && spid.isEnabled()) {
            SpidAuthenticationSuccessHandler successHandler = successHandlerProvider.getIfAvailable();
            Saml2AuthenticationRequestResolver authenticationRequestResolver = authnRequestResolverProvider.getIfAvailable();
            http.saml2Login(saml -> {
                if (successHandler != null) {
                    saml.successHandler(successHandler);
                }
                if (authenticationRequestResolver != null) {
                    saml.authenticationRequestResolver(authenticationRequestResolver);
                }
            });
            String logoutTarget = Optional.ofNullable(spid.getPostLoginRedirect()).orElse("/verifier");
            http.logout(logout -> logout.logoutSuccessUrl(logoutTarget));
        }
        return http.build();
    }
}
