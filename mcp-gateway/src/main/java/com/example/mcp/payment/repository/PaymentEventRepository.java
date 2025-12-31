package com.example.mcp.payment.repository;

import com.example.mcp.payment.model.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, String> {

    Optional<PaymentEvent> findByProviderEventId(String providerEventId);

    List<PaymentEvent> findByPaymentIdOrderByCreatedAtDesc(String paymentId);

    boolean existsByProviderEventId(String providerEventId);
}
