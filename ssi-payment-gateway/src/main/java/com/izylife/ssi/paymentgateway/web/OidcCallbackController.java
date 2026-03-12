package com.izylife.ssi.paymentgateway.web;

import com.izylife.ssi.paymentgateway.model.PaymentTransaction;
import com.izylife.ssi.paymentgateway.service.PaymentCallbackResult;
import com.izylife.ssi.paymentgateway.service.PaymentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;

@Controller
@RequestMapping("/oidc")
public class OidcCallbackController {

    private final PaymentService paymentService;

    public OidcCallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/callback")
    public ResponseEntity<?> handleCallback(@RequestParam("state") String state,
                                            @RequestParam("code") String code) {
        PaymentCallbackResult result = paymentService.handleOidcCallback(state, code);
        PaymentTransaction tx = result.transaction();
        URI redirectUri = result.redirectUri();
        if (redirectUri != null) {
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, redirectUri.toString())
                    .build();
        }
        return ResponseEntity.ok("Payment " + tx.getId() + " status " + tx.getStatus());
    }
}
