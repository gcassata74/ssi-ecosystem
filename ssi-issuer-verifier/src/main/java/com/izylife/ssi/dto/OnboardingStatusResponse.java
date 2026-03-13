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

package com.izylife.ssi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OnboardingStatusResponse {

    private String currentStep;
    private String issuerState;
    private OnboardingQrResponse verifier;
    private OnboardingQrResponse issuer;
    private String verifierError;
    private String authorizationCode;
    private String authorizationRedirectUri;
    private String authorizationState;

    public OnboardingStatusResponse() {
    }

    public OnboardingStatusResponse(String currentStep, OnboardingQrResponse verifier, OnboardingQrResponse issuer) {
        this(currentStep, null, verifier, issuer);
    }

    public OnboardingStatusResponse(String currentStep, String issuerState, OnboardingQrResponse verifier, OnboardingQrResponse issuer) {
        this.currentStep = currentStep;
        this.issuerState = issuerState;
        this.verifier = verifier;
        this.issuer = issuer;
        this.verifierError = null;
    }
}
