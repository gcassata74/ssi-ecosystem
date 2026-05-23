/*
 * SSI Verifier
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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private VerifierProperties verifier = new VerifierProperties();
    private CorsProperties cors = new CorsProperties();
    private KeycloakProperties keycloak = new KeycloakProperties();

    @Getter
    @Setter
    public static class VerifierProperties {
        private String endpoint;
        private String qrPayload;
        private String challenge;
        private String clientId;
        private String clientIdScheme = "redirect_uri";
        private String responseMode = "direct_post";
        private String requestAudience = "https://self-issued.me/v2";
        private String presentationDefinitionId = "staff-credential";
        private SigningKeyProperties signingKey = new SigningKeyProperties();
    }

    @Getter
    @Setter
    public static class CorsProperties {
        private List<String> allowedOrigins = List.of("*");
        private boolean allowCredentials;
    }

    @Getter
    @Setter
    public static class KeycloakProperties {
        private String baseUrl = "http://localhost:8180";
        private String realm = "master";
    }

    @Getter
    @Setter
    public static class SigningKeyProperties {
        private String kid;
        private String kty;
        private String crv;
        private String x;
        private String y;
        private String d;
        private String alg = "ES256";
    }
}
