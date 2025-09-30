package com.izylife.ssi.controller;

import com.izylife.ssi.dto.PocQrResponse;
import com.izylife.ssi.service.QrCodeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/api/poc", produces = MediaType.APPLICATION_JSON_VALUE)
public class PocQrController {

    private final QrCodeService qrCodeService;

    public PocQrController(QrCodeService qrCodeService) {
        this.qrCodeService = qrCodeService;
    }

    @GetMapping("/vp-request")
    public PocQrResponse getTestVpRequestQr() {
        String state = UUID.randomUUID().toString();
        String nonce = "demo-nonce-" + state.substring(0, 8);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", "https://verifier.izylife.example.org/callback");
        params.put("client_id_scheme", "redirect_uri");
        params.put("request_uri", "https://verifier.izylife.example.org/oidc4vp/requests/" + state);
        params.put("response_type", "vp_token");
        params.put("response_mode", "direct_post");
        params.put("scope", "openid");
        params.put("nonce", nonce);
        params.put("state", state);
        params.put("presentation_definition_uri", "https://verifier.izylife.example.org/definitions/staff-credential.json");

        String query = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        String payload = "openid://?" + query;

        String instructions = "OIDC4VP Authorization Request\n" +
                "1. Scan with an OIDC4VP-capable wallet.\n" +
                "2. The wallet resolves the request_uri and validates the signed request object.\n" +
                "3. Present a credential that matches the referenced presentation definition.";

        String label = "OIDC4VP Demo Request";
        String qrImage = qrCodeService.generatePngDataUri(payload);
        return new PocQrResponse(label, instructions, payload, qrImage);
    }
}
