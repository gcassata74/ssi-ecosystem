package com.izylife.ssi.controller;

import com.izylife.ssi.dto.VerifyPresentationRequest;
import com.izylife.ssi.dto.VerifyPresentationResponse;
import com.izylife.ssi.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/verification", produces = MediaType.APPLICATION_JSON_VALUE)
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping(path = "/presentations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public VerifyPresentationResponse verifyPresentation(@Valid @RequestBody VerifyPresentationRequest request) {
        return verificationService.verifyPresentation(request);
    }
}
