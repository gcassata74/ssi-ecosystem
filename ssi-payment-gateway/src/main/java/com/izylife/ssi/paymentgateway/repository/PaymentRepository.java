package com.izylife.ssi.paymentgateway.repository;

import com.izylife.ssi.paymentgateway.model.PaymentTransaction;

import java.util.Optional;

public interface PaymentRepository {
    PaymentTransaction save(PaymentTransaction transaction);

    Optional<PaymentTransaction> findById(String id);

    Optional<PaymentTransaction> findByState(String state);
}
