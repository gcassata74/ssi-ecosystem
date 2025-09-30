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
