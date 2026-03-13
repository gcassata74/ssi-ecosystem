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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public CorsConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsConfiguration configuration = buildCorsConfiguration();

        var registration = registry.addMapping("/**");

        if (!CollectionUtils.isEmpty(configuration.getAllowedOriginPatterns())) {
            registration.allowedOriginPatterns(configuration.getAllowedOriginPatterns().toArray(String[]::new));
        }

        if (!CollectionUtils.isEmpty(configuration.getAllowedMethods())) {
            registration.allowedMethods(configuration.getAllowedMethods().toArray(String[]::new));
        }

        if (!CollectionUtils.isEmpty(configuration.getAllowedHeaders())) {
            registration.allowedHeaders(configuration.getAllowedHeaders().toArray(String[]::new));
        }

        if (!CollectionUtils.isEmpty(configuration.getExposedHeaders())) {
            registration.exposedHeaders(configuration.getExposedHeaders().toArray(String[]::new));
        }

        if (configuration.getAllowCredentials() != null) {
            registration.allowCredentials(configuration.getAllowCredentials());
        }

        if (configuration.getMaxAge() != null) {
            registration.maxAge(configuration.getMaxAge());
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = buildCorsConfiguration();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private CorsConfiguration buildCorsConfiguration() {
        var corsProps = appProperties.getCors();

        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = corsProps.getAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOriginPatterns(origins);
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("*"));
        configuration.setAllowCredentials(corsProps.isAllowCredentials());
        configuration.setMaxAge(3600L);

        return configuration;
    }
}
