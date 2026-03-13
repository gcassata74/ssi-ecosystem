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

package com.izylife.ssi.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Service
public class QrCodeService {

    private static final int DEFAULT_QR_SIZE = 280;

    public String generatePngDataUri(String payload) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, DEFAULT_QR_SIZE, DEFAULT_QR_SIZE);
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
                String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                return "data:image/png;base64," + base64;
            }
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Unable to generate QR code from payload", ex);
        }
    }
}
