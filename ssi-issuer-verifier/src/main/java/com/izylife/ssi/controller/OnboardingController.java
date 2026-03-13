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

package com.izylife.ssi.controller;

import com.izylife.ssi.dto.OnboardingQrResponse;
import com.izylife.ssi.dto.OnboardingStatusResponse;
import com.izylife.ssi.service.OnboardingStateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/onboarding", produces = MediaType.APPLICATION_JSON_VALUE)
public class OnboardingController {

    private final OnboardingStateService onboardingStateService;

    public OnboardingController(OnboardingStateService onboardingStateService) {
        this.onboardingStateService = onboardingStateService;
    }

    @GetMapping("/qr")
    public OnboardingStatusResponse getCurrentQr(@RequestParam(value = "client_id", required = false) String clientId,
                                                 @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                                 @RequestParam(value = "state", required = false) String state) {
        onboardingStateService.updateClientContext(clientId, redirectUri, state);
        return onboardingStateService.getCurrentStatus();
    }

    @GetMapping("/issuer")
    public OnboardingQrResponse getIssuerQr(@RequestParam(value = "client_id", required = false) String clientId,
                                            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                            @RequestParam(value = "state", required = false) String state) {
        onboardingStateService.updateClientContext(clientId, redirectUri, state);
        return onboardingStateService.getIssuerQr();
    }

    @PostMapping("/issuer/credentials-received")
    public ResponseEntity<OnboardingStatusResponse> acknowledgeIssuerCredentialReceipt() {
        boolean acknowledged = onboardingStateService.acknowledgeIssuerCredentialsReceived();
        OnboardingStatusResponse status = onboardingStateService.getCurrentStatus();
        if (acknowledged) {
            return ResponseEntity.ok(status);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(status);
    }
}
