package com.example.mcp.payment.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_event", indexes = {
    @Index(name = "idx_payment_event_payment_id", columnList = "payment_id"),
    @Index(name = "idx_payment_event_provider_event_id", columnList = "provider_event_id", unique = true)
})
public class PaymentEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "payment_id", nullable = false, length = 36)
    private String paymentId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed", nullable = false)
    private boolean processed = false;

    public PaymentEvent() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public PaymentEvent(String paymentId, String eventType, String providerEventId, String payload) {
        this();
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.providerEventId = providerEventId;
        this.payload = payload;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getProviderEventId() { return providerEventId; }
    public void setProviderEventId(String providerEventId) { this.providerEventId = providerEventId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isProcessed() { return processed; }
    public void setProcessed(boolean processed) { this.processed = processed; }
}
