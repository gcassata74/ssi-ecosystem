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
public class OnboardingQrResponse {

    private String step;
    private String title;
    private String description;
    private String helperText;
    private String qrCodePayload;
    private String qrCodeImageDataUrl;
    private String actionLabel;
    private String actionUrl;
    private CredentialPreviewDto credentialPreview;

    public OnboardingQrResponse() {
    }

    public OnboardingQrResponse(String step, String title, String description, String helperText, String qrCodePayload, String qrCodeImageDataUrl) {
        this(step, title, description, helperText, qrCodePayload, qrCodeImageDataUrl, null, null);
    }

    public OnboardingQrResponse(String step, String title, String description, String helperText, String qrCodePayload, String qrCodeImageDataUrl, String actionLabel, String actionUrl) {
        this.step = step;
        this.title = title;
        this.description = description;
        this.helperText = helperText;
        this.qrCodePayload = qrCodePayload;
        this.qrCodeImageDataUrl = qrCodeImageDataUrl;
        this.actionLabel = actionLabel;
        this.actionUrl = actionUrl;
    }

    public OnboardingQrResponse withCredentialPreview(CredentialPreviewDto preview) {
        this.credentialPreview = preview;
        return this;
    }
}
