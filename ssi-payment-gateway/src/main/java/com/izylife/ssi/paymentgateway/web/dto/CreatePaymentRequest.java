package com.izylife.ssi.paymentgateway.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull @DecimalMin(value = "0.10", message = "Amount must be at least 0.10") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 140) String description,
        @URL(message = "returnUrl must be a valid URL")
        String returnUrl
) {
}
