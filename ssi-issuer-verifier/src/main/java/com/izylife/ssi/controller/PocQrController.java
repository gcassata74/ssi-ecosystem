package com.izylife.ssi.controller;

import com.izylife.ssi.dto.PocQrResponse;
import com.izylife.ssi.service.Oidc4VpRequestService;
import com.izylife.ssi.service.QrCodeService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/poc", produces = MediaType.APPLICATION_JSON_VALUE)
public class PocQrController {

    private final QrCodeService qrCodeService;
    private final Oidc4VpRequestService oidc4VpRequestService;

    public PocQrController(QrCodeService qrCodeService, Oidc4VpRequestService oidc4VpRequestService) {
        this.qrCodeService = qrCodeService;
        this.oidc4VpRequestService = oidc4VpRequestService;
    }

    @GetMapping("/vp-request")
    public PocQrResponse getTestVpRequestQr() {
        Oidc4VpRequestService.AuthorizationRequest authorizationRequest = oidc4VpRequestService.createAuthorizationRequest(null, null, null);

        String payload = authorizationRequest.qrPayload();

        String instructions = "OIDC4VP Authorization Request\n" +
                "1. Scan with an OIDC4VP-capable wallet.\n" +
                "2. The wallet resolves the request_uri and validates the signed request object.\n" +
                "3. Present a credential that matches the referenced presentation definition.";

        String label = "OIDC4VP Demo Request";
        String qrImage = qrCodeService.generatePngDataUri(payload);
        return new PocQrResponse(label, instructions, payload, qrImage);
    }
}
