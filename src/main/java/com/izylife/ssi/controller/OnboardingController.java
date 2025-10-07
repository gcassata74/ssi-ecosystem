package com.izylife.ssi.controller;

import com.izylife.ssi.dto.OnboardingStatusResponse;
import com.izylife.ssi.service.OnboardingStateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/onboarding", produces = MediaType.APPLICATION_JSON_VALUE)
public class OnboardingController {

    private final OnboardingStateService onboardingStateService;

    public OnboardingController(OnboardingStateService onboardingStateService) {
        this.onboardingStateService = onboardingStateService;
    }

    @GetMapping("/qr")
    public OnboardingStatusResponse getCurrentQr() {
        return onboardingStateService.getCurrentStatus();
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
