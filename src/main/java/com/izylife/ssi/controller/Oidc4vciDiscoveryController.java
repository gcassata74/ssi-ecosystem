package com.izylife.ssi.controller;

import com.izylife.ssi.dto.oidc4vci.AuthorizationServerMetadata;
import com.izylife.ssi.dto.oidc4vci.CredentialIssuerMetadata;
import com.izylife.ssi.service.Oidc4vciService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class Oidc4vciDiscoveryController {

    private final Oidc4vciService oidc4vciService;

    public Oidc4vciDiscoveryController(Oidc4vciService oidc4vciService) {
        this.oidc4vciService = oidc4vciService;
    }

    @GetMapping("/.well-known/openid-credential-issuer")
    public CredentialIssuerMetadata getCredentialIssuerMetadata() {
        return oidc4vciService.buildCredentialIssuerMetadata();
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public AuthorizationServerMetadata getAuthorizationServerMetadata() {
        return oidc4vciService.buildAuthorizationServerMetadata();
    }
}
