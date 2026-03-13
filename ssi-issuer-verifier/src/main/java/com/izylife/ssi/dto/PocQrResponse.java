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
public class PocQrResponse {

    private String label;
    private String instructions;
    private String payload;
    private String qrImageDataUrl;

    public PocQrResponse() {
    }

    public PocQrResponse(String label, String instructions, String payload, String qrImageDataUrl) {
        this.label = label;
        this.instructions = instructions;
        this.payload = payload;
        this.qrImageDataUrl = qrImageDataUrl;
    }
}
