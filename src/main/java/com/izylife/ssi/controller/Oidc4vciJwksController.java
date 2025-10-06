package com.izylife.ssi.controller;

import com.izylife.ssi.service.IssuerSigningService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class Oidc4vciJwksController {

    private final IssuerSigningService issuerSigningService;

    public Oidc4vciJwksController(IssuerSigningService issuerSigningService) {
        this.issuerSigningService = issuerSigningService;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> getJwks() {
        return issuerSigningService.getPublicJwkSet().toJSONObject();
    }
}
