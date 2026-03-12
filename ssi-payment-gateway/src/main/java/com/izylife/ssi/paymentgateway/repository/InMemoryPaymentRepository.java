package com.izylife.ssi.paymentgateway.repository;

import com.izylife.ssi.paymentgateway.model.PaymentTransaction;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<String, PaymentTransaction> store = new ConcurrentHashMap<>();

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        transaction.touch();
        store.put(transaction.getId(), transaction);
        return transaction;
    }

    @Override
    public Optional<PaymentTransaction> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<PaymentTransaction> findByState(String state) {
        return store.values().stream()
                .filter(tx -> tx.getState() != null && tx.getState().equals(state))
                .findFirst();
    }
}
