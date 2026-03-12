package com.izylife.ssi.paymentgateway.web;

import com.izylife.ssi.paymentgateway.service.PaymentService;
import com.izylife.ssi.paymentgateway.web.dto.CreatePaymentRequest;
import com.izylife.ssi.paymentgateway.web.dto.PaymentView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentView> create(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentView view = paymentService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentView> find(@PathVariable String paymentId) {
        return ResponseEntity.ok(paymentService.findPayment(paymentId));
    }
}
