package com.izylife.ssi.controller;

import com.izylife.ssi.dto.OnboardingStatusResponse;
import com.izylife.ssi.service.OnboardingStateService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
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
}
