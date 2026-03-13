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

package com.izylife.ssi.service;

import com.izylife.ssi.config.AppProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.Base64URL;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class IssuerSigningService {

    private final JWSSigner signer;
    private final ECKey signingKey;
    private final JWSAlgorithm algorithm;
    private final JWSHeader headerTemplate;
    private final JWKSet publicJwkSet;

    public IssuerSigningService(AppProperties appProperties) {
        AppProperties.SigningKeyProperties keyProps = Objects.requireNonNull(appProperties.getIssuer().getSigningKey(),
                "Issuer signing key configuration is required");
        if (keyProps.getKid() == null || keyProps.getKid().isBlank()
                || keyProps.getCrv() == null || keyProps.getCrv().isBlank()
                || keyProps.getX() == null || keyProps.getX().isBlank()
                || keyProps.getY() == null || keyProps.getY().isBlank()
                || keyProps.getD() == null || keyProps.getD().isBlank()
                || keyProps.getKty() == null || keyProps.getKty().isBlank()) {
            throw new IllegalStateException("Incomplete issuer signing key configuration");
        }
        if (!"EC".equalsIgnoreCase(keyProps.getKty())) {
            throw new IllegalStateException("Only EC signing keys are supported for JWT credentials");
        }
        Curve curve = resolveCurve(keyProps.getCrv());
        try {
            this.signingKey = new ECKey.Builder(
                    curve,
                    new Base64URL(keyProps.getX()),
                    new Base64URL(keyProps.getY()))
                    .d(new Base64URL(keyProps.getD()))
                    .keyID(keyProps.getKid())
                    .build();
            this.signer = new ECDSASigner(signingKey);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to load issuer signing key", ex);
        }

        this.algorithm = keyProps.getAlg() != null ? JWSAlgorithm.parse(keyProps.getAlg()) : JWSAlgorithm.ES256;
        this.headerTemplate = new JWSHeader.Builder(this.algorithm)
                .type(JOSEObjectType.JWT)
                .keyID(signingKey.getKeyID())
                .build();
        this.publicJwkSet = new JWKSet(signingKey.toPublicJWK());
    }

    private Curve resolveCurve(String crv) {
        if (crv == null) {
            throw new IllegalStateException("EC curve is required for issuer signing key");
        }
        return switch (crv) {
            case "P-256" -> Curve.P_256;
            case "P-384" -> Curve.P_384;
            case "P-521" -> Curve.P_521;
            default -> throw new IllegalStateException("Unsupported curve for issuer signing key: " + crv);
        };
    }

    public JWSSigner getSigner() {
        return signer;
    }

    public JWSHeader getHeaderTemplate() {
        return headerTemplate;
    }

    public JWSAlgorithm getAlgorithm() {
        return algorithm;
    }

    public JWKSet getPublicJwkSet() {
        return publicJwkSet;
    }
}
