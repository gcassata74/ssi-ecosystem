package com.izylife.ssi.paymentgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "ssi.issuer")
public class IssuerClientProperties {

    /**
     * Base URL of the issuer/verifier portal (e.g. http://localhost:9090).
     */
    private URI baseUrl = URI.create("http://localhost:9090");

    /**
     * Path or absolute URL of the authorization endpoint.
     */
    private String authorizationPath = "/oauth2/authorize";

    /**
     * Path or absolute URL of the token endpoint.
     */
    private String tokenPath = "/oauth2/token";

    private String clientId;
    private String clientSecret;

    private URI redirectUri = URI.create("http://localhost:9092/oidc/callback");

    private String scope = "openid profile credential_preview";

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthorizationPath() {
        return authorizationPath;
    }

    public void setAuthorizationPath(String authorizationPath) {
        this.authorizationPath = authorizationPath;
    }

    public String getTokenPath() {
        return tokenPath;
    }

    public void setTokenPath(String tokenPath) {
        this.tokenPath = tokenPath;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public URI getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(URI redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public URI resolveAuthorizationEndpoint() {
        return resolve(baseUrl, authorizationPath);
    }

    public URI resolveTokenEndpoint() {
        return resolve(baseUrl, tokenPath);
    }

    private static URI resolve(URI base, String pathOrUrl) {
        if (pathOrUrl == null) {
            return base;
        }
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return URI.create(pathOrUrl);
        }
        String baseStr = base.toString();
        if (baseStr.endsWith("/") && pathOrUrl.startsWith("/")) {
            return URI.create(baseStr.substring(0, baseStr.length() - 1) + pathOrUrl);
        }
        if (!baseStr.endsWith("/") && !pathOrUrl.startsWith("/")) {
            return URI.create(baseStr + "/" + pathOrUrl);
        }
        return URI.create(baseStr + pathOrUrl);
    }
}
