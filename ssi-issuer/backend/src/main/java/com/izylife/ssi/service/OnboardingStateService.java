/*
 * SSI Issuer
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.izylife.ssi.config.AppProperties;
import com.izylife.ssi.dto.OnboardingQrResponse;
import com.izylife.ssi.dto.OnboardingStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OnboardingStateService {

    public enum OnboardingStep {
        ISSUER_SPID_PROMPT,
        ISSUER_OIDC_PROMPT,
        ISSUER_QR
    }

    public enum IssuerFlowState {
        IDLE,
        WAITING_FOR_WALLET,
        CREDENTIALS_RECEIVED
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingStateService.class);
    private static final String ONBOARDING_TOPIC = "/topic/onboarding";

    private final AtomicReference<OnboardingStep> currentStep = new AtomicReference<>(OnboardingStep.ISSUER_QR);
    private final AtomicReference<IssuerFlowState> issuerFlowState = new AtomicReference<>(IssuerFlowState.IDLE);
    private final AtomicReference<CredentialOfferContext> activeCredentialOffer = new AtomicReference<>();

    private final AppProperties appProperties;
    private final QrCodeService qrCodeService;
    private final Oidc4vciService oidc4vciService;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public OnboardingStateService(AppProperties appProperties,
                                  QrCodeService qrCodeService,
                                  Oidc4vciService oidc4vciService,
                                  ObjectMapper objectMapper,
                                  SimpMessagingTemplate messagingTemplate) {
        this.appProperties = appProperties;
        this.qrCodeService = qrCodeService;
        this.oidc4vciService = oidc4vciService;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    public OnboardingStatusResponse getCurrentStatus() {
        OnboardingQrResponse issuer = buildIssuerState();
        return new OnboardingStatusResponse(currentStep.get().name(), issuerFlowState.get().name(), null, issuer);
    }

    public OnboardingQrResponse getIssuerQr(String realm) {
        OnboardingStep step = currentStep.get();
        if (step == OnboardingStep.ISSUER_SPID_PROMPT) {
            return buildSpidPrompt();
        }
        if (step == OnboardingStep.ISSUER_OIDC_PROMPT) {
            return buildOidcPrompt(realm);
        }
        // Active credential offer (post-authentication) takes priority over config-driven prompts
        CredentialOfferContext ctx = activeCredentialOffer.get();
        if (ctx != null && oidc4vciService.findOfferById(ctx.offer().offerId()).isPresent()) {
            return buildCredentialOfferQr();
        }
        boolean spidEnabled = Optional.ofNullable(appProperties.getSpid())
                .map(AppProperties.SpidProperties::isEnabled)
                .orElse(false);
        if (spidEnabled) {
            return buildSpidPrompt();
        }
        boolean oidcEnabled = Optional.ofNullable(appProperties.getKeycloak())
                .map(AppProperties.KeycloakProperties::isOidcEnabled)
                .orElse(false);
        if (oidcEnabled) {
            return buildOidcPrompt(realm);
        }
        return buildCredentialOfferQr();
    }

    public void promptIssuerEnrollment() {
        AppProperties.SpidProperties spidProperties = appProperties.getSpid();
        boolean oidcEnabled = Optional.ofNullable(appProperties.getKeycloak())
                .map(AppProperties.KeycloakProperties::isOidcEnabled)
                .orElse(false);

        if (spidProperties != null && spidProperties.isEnabled()) {
            currentStep.set(OnboardingStep.ISSUER_SPID_PROMPT);
            issuerFlowState.set(IssuerFlowState.WAITING_FOR_WALLET);
            activeCredentialOffer.set(null);
            publishUpdate(OnboardingStep.ISSUER_SPID_PROMPT, buildSpidPrompt());
            return;
        }
        if (oidcEnabled) {
            currentStep.set(OnboardingStep.ISSUER_OIDC_PROMPT);
            issuerFlowState.set(IssuerFlowState.WAITING_FOR_WALLET);
            activeCredentialOffer.set(null);
            publishUpdate(OnboardingStep.ISSUER_OIDC_PROMPT, buildOidcPrompt(null));
            return;
        }
        CredentialOfferContext context = activeCredentialOffer.get();
        if (context == null) {
            activeCredentialOffer.compareAndSet(null, createCredentialOfferContext(buildDefaultStaffProfile()));
        }
        showIssuerCredentialOffer();
    }

    public void showIssuerCredentialOffer() {
        currentStep.set(OnboardingStep.ISSUER_QR);
        issuerFlowState.set(IssuerFlowState.WAITING_FOR_WALLET);
        publishUpdate(OnboardingStep.ISSUER_QR, buildCredentialOfferQr());
    }

    public boolean acknowledgeIssuerCredentialsReceived() {
        boolean transitioned = issuerFlowState.compareAndSet(
                IssuerFlowState.WAITING_FOR_WALLET,
                IssuerFlowState.CREDENTIALS_RECEIVED
        );
        if (transitioned) {
            issuerFlowState.set(IssuerFlowState.IDLE);
            OnboardingStatusResponse status = getCurrentStatus();
            messagingTemplate.convertAndSend(ONBOARDING_TOPIC, status);
        }
        return transitioned;
    }

    public void completeIssuerEnrollmentWithSpid(Oidc4vciService.StaffProfile profile) {
        if (profile == null) {
            return;
        }
        activeCredentialOffer.set(createCredentialOfferContext(profile));
        showIssuerCredentialOffer();
    }

    public void completeIssuerEnrollmentWithKeycloak(Map<String, Object> credentialSubject,
                                                      String issuerDid,
                                                      Map<String, Object> privateJwk,
                                                      String realm) {
        if (credentialSubject == null || issuerDid == null || privateJwk == null) {
            return;
        }
        Oidc4vciService.StaffProfile profile = new Oidc4vciService.StaffProfile(
                valueAsString(credentialSubject.getOrDefault("id", issuerDid), issuerDid),
                valueAsString(credentialSubject.get("familyName"), ""),
                valueAsString(credentialSubject.get("givenName"), ""),
                valueAsString(credentialSubject.get("role"), ""),
                valueAsString(credentialSubject.get("employeeNumber"), ""),
                valueAsString(credentialSubject.get("email"), null)
        );
        Oidc4vciService.CredentialOfferRecord offer =
                oidc4vciService.createStaffCredentialOffer(profile, issuerDid, privateJwk, credentialSubject);
        try {
            String helperText = String.format("realm=%s | issuer_state=%s | pre-authorized grant available",
                    realm == null ? "n/a" : realm, offer.issuerState());
            String offerJson = objectMapper.writeValueAsString(oidc4vciService.buildCredentialOffer(offer));
            String encoded = java.net.URLEncoder.encode(offerJson, StandardCharsets.UTF_8);
            String qrPayload = "openid-credential-offer://?credential_offer=" + encoded;
            activeCredentialOffer.set(new CredentialOfferContext(offer, profile, helperText, qrPayload));
            showIssuerCredentialOffer();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialise credential offer", ex);
        }
    }

    public OnboardingStep getCurrentStep() {
        return currentStep.get();
    }

    private OnboardingQrResponse buildIssuerState() {
        OnboardingStep step = currentStep.get();
        if (step == OnboardingStep.ISSUER_SPID_PROMPT) {
            return buildSpidPrompt();
        }
        if (step == OnboardingStep.ISSUER_OIDC_PROMPT) {
            return buildOidcPrompt(null);
        }
        return resolveConfiguredIssuerPayload()
                .map(this::buildConfiguredIssuerQr)
                .orElseGet(this::buildCredentialOfferQr);
    }

    private Optional<String> resolveConfiguredIssuerPayload() {
        return Optional.ofNullable(appProperties.getIssuer())
                .map(AppProperties.IssuerProperties::getCredentialOfferUri)
                .filter(value -> !value.isBlank());
    }

    private OnboardingQrResponse buildConfiguredIssuerQr(String payload) {
        String organisation = resolveIssuerOrganisation();
        String description = String.format("No credential found in the wallet. Issue one from %s.", organisation);
        return new OnboardingQrResponse(
                OnboardingStep.ISSUER_QR.name(),
                "Get an Izylife Credential",
                description,
                "Scan to open the issuer onboarding experience.",
                payload,
                qrCodeService.generatePngDataUri(payload)
        );
    }

    private OnboardingQrResponse buildCredentialOfferQr() {
        CredentialOfferContext context = ensureCredentialOfferContext();
        String description = "Wallet has no Izylife staff credential. Scan to start an OIDC4VCI credential offer.";
        return new OnboardingQrResponse(
                OnboardingStep.ISSUER_QR.name(),
                "Import Izylife Staff Credential",
                description,
                context.helperText(),
                context.qrPayload(),
                qrCodeService.generatePngDataUri(context.qrPayload())
        );
    }

    private OnboardingQrResponse buildSpidPrompt() {
        AppProperties.SpidProperties spid = Optional.ofNullable(appProperties.getSpid()).orElseGet(AppProperties.SpidProperties::new);
        String description = "Autenticati con SPID per generare l'offerta di credenziali del personale Izylife.";
        String loginUrl = resolveSpidLoginUrl(spid);
        return new OnboardingQrResponse(
                OnboardingStep.ISSUER_SPID_PROMPT.name(),
                "Accesso richiesto",
                description,
                "Avvia l'autenticazione SPID per proseguire.",
                null,
                null,
                "Entra con SPID",
                loginUrl
        );
    }

    private OnboardingQrResponse buildOidcPrompt(String realm) {
        String effectiveRealm = (realm != null && !realm.isBlank())
                ? realm
                : Optional.ofNullable(appProperties.getKeycloak()).map(AppProperties.KeycloakProperties::getRealm).filter(r -> r != null && !r.isBlank()).orElse("master");
        return new OnboardingQrResponse(
                OnboardingStep.ISSUER_OIDC_PROMPT.name(),
                "Login richiesto",
                "Autenticati con Keycloak per generare l'offerta di credenziali.",
                "Avvia l'autenticazione Keycloak per proseguire.",
                null,
                null,
                "Entra con Keycloak",
                "/oauth2/authorization/" + effectiveRealm
        );
    }

    private String resolveSpidLoginUrl(AppProperties.SpidProperties spid) {
        String rawPath = Optional.ofNullable(spid.getLoginPath()).filter(path -> !path.isBlank()).orElse("/saml2/authenticate/" + spid.getRegistrationId());
        if (rawPath.startsWith("http://") || rawPath.startsWith("https://")) {
            return rawPath;
        }
        String baseUrl = Optional.ofNullable(appProperties.getIssuer())
                .map(AppProperties.IssuerProperties::getEndpoint)
                .filter(v -> v != null && !v.isBlank())
                .orElse("");
        if (baseUrl.isBlank()) {
            return rawPath;
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return rawPath.startsWith("/") ? baseUrl + rawPath : baseUrl + "/" + rawPath;
    }

    private CredentialOfferContext ensureCredentialOfferContext() {
        CredentialOfferContext context = activeCredentialOffer.get();
        if (context != null && oidc4vciService.findOfferById(context.offer().offerId()).isPresent()) {
            return context;
        }
        CredentialOfferContext refreshed = createCredentialOfferContext(buildDefaultStaffProfile());
        activeCredentialOffer.set(refreshed);
        return refreshed;
    }

    private CredentialOfferContext createCredentialOfferContext(Oidc4vciService.StaffProfile profile) {
        Oidc4vciService.CredentialOfferRecord offer = oidc4vciService.createStaffCredentialOffer(profile);
        try {
            String helperText = String.format("issuer_state=%s | pre-authorized grant available", offer.issuerState());
            String offerJson = objectMapper.writeValueAsString(oidc4vciService.buildCredentialOffer(offer));
            String encoded = URLEncoder.encode(offerJson, StandardCharsets.UTF_8);
            String qrPayload = "openid-credential-offer://?credential_offer=" + encoded;
            return new CredentialOfferContext(offer, profile, helperText, qrPayload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialise credential offer", ex);
        }
    }

    private Oidc4vciService.StaffProfile buildDefaultStaffProfile() {
        return new Oidc4vciService.StaffProfile(
                "did:key:z6MkjsPve3QFtSobhVYqgv48tSxB6v6y7sgbhR8nTBiq7bYd",
                "Rivera",
                "Jamie",
                "Public Authority Operator",
                "IZY-OPS-001",
                "jamie.rivera@izylife.example"
        );
    }

    private String resolveIssuerOrganisation() {
        return Optional.ofNullable(appProperties.getIssuer())
                .map(AppProperties.IssuerProperties::getOrganizationName)
                .filter(v -> !v.isBlank())
                .orElse("the Izylife issuer");
    }

    private String valueAsString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        return String.valueOf(value);
    }

    private void publishUpdate(OnboardingStep activeStep, OnboardingQrResponse stepQr) {
        OnboardingQrResponse issuer = buildIssuerState();
        if (activeStep == OnboardingStep.ISSUER_SPID_PROMPT
                || activeStep == OnboardingStep.ISSUER_OIDC_PROMPT
                || activeStep == OnboardingStep.ISSUER_QR) {
            issuer = stepQr;
        }
        OnboardingStatusResponse status = new OnboardingStatusResponse(
                activeStep.name(),
                issuerFlowState.get().name(),
                null,
                issuer
        );
        LOGGER.debug("Publishing issuer onboarding update: step={} issuerState={}", status.getCurrentStep(), status.getIssuerState());
        messagingTemplate.convertAndSend(ONBOARDING_TOPIC, status);
    }

    private record CredentialOfferContext(Oidc4vciService.CredentialOfferRecord offer,
                                          Oidc4vciService.StaffProfile profile,
                                          String helperText,
                                          String qrPayload) {
    }
}
