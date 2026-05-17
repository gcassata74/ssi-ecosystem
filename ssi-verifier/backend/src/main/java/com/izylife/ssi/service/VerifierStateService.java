/*
 * SSI Verifier
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

import com.izylife.ssi.dto.CredentialPreviewDto;
import com.izylife.ssi.dto.OnboardingQrResponse;
import com.izylife.ssi.dto.OnboardingStatusResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages verification portal state and publishes WebSocket events on /topic/verification.
 */
@Service
public class VerifierStateService {

    private static final String VERIFICATION_TOPIC = "/topic/verification";

    private final Oidc4VpRequestService oidc4VpRequestService;
    private final QrCodeService qrCodeService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicReference<CredentialPreviewDto> lastVerifiedCredential = new AtomicReference<>();

    public VerifierStateService(Oidc4VpRequestService oidc4VpRequestService,
                                QrCodeService qrCodeService,
                                SimpMessagingTemplate messagingTemplate) {
        this.oidc4VpRequestService = oidc4VpRequestService;
        this.qrCodeService = qrCodeService;
        this.messagingTemplate = messagingTemplate;
    }

    public boolean isActiveAuthorizationState(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        return oidc4VpRequestService.resolveSession(state).isPresent();
    }

    public void clearVerifiedCredential() {
        lastVerifiedCredential.set(null);
    }

    public void recordVerifiedCredential(CredentialPreviewDto preview) {
        if (preview != null) {
            lastVerifiedCredential.set(preview);
        }
        publishVerifierQr();
    }

    public void showVerifierQr() {
        publishVerifierQr();
    }

    public void publishVerifierError(String message) {
        OnboardingStatusResponse status = buildVerifierStatus();
        status.setVerifierError(message);
        messagingTemplate.convertAndSend(VERIFICATION_TOPIC, status);
    }

    public void publishAuthorizationCode(String code, String state, String redirectUri) {
        OnboardingStatusResponse status = buildVerifierStatus();
        status.setAuthorizationCode(code);
        status.setAuthorizationState(state);
        status.setAuthorizationRedirectUri(redirectUri);
        messagingTemplate.convertAndSend(VERIFICATION_TOPIC, status);
    }

    public CredentialPreviewDto getLastVerifiedCredential() {
        return lastVerifiedCredential.get();
    }

    public OnboardingQrResponse getCurrentQr() {
        return buildCurrentVerifierQr(lastVerifiedCredential.get());
    }

    private void publishVerifierQr() {
        OnboardingStatusResponse status = buildVerifierStatus();
        messagingTemplate.convertAndSend(VERIFICATION_TOPIC, status);
    }

    private OnboardingStatusResponse buildVerifierStatus() {
        CredentialPreviewDto preview = lastVerifiedCredential.get();
        return new OnboardingStatusResponse("VP_REQUEST", "IDLE", buildCurrentVerifierQr(preview), null);
    }

    private OnboardingQrResponse buildCurrentVerifierQr(CredentialPreviewDto preview) {
        Oidc4VpRequestService.AuthorizationRequest authorization = oidc4VpRequestService.createAuthorizationRequest(null, null, null);
        String payload = authorization.qrPayload();
        String helperText = "State: " + authorization.state() + " | Nonce: " + authorization.nonce();
        OnboardingQrResponse qr = new OnboardingQrResponse(
                "VP_REQUEST",
                "Verifiable Presentation Request",
                "Scan this code with your SSI wallet to continue the verification flow.",
                helperText,
                payload,
                qrCodeService.generatePngDataUri(payload)
        );
        return qr.withCredentialPreview(preview);
    }
}
