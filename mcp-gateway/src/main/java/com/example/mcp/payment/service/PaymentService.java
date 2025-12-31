package com.example.mcp.payment.service;

import com.example.mcp.payment.model.Payment;
import com.example.mcp.payment.model.PaymentEvent;
import com.example.mcp.payment.model.PaymentStatus;
import com.example.mcp.payment.repository.PaymentEventRepository;
import com.example.mcp.payment.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private boolean mockMode = false;

    public PaymentService(PaymentRepository paymentRepository, PaymentEventRepository paymentEventRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
    }

    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
            log.info("Stripe API initialized");
        } else {
            mockMode = true;
            log.warn("Stripe not configured - running in MOCK MODE (payments are simulated)");
        }
    }

    public boolean isMockMode() {
        return mockMode;
    }

    @Transactional
    public Payment createCheckoutSession(String email, BigDecimal amount, String currency) throws StripeException {
        // Create local payment record
        Payment payment = new Payment(email, amount, currency != null ? currency : "EUR");
        payment.setStatus(PaymentStatus.PENDING);

        if (mockMode) {
            // Mock mode - create simulated checkout
            String mockSessionId = "mock_session_" + payment.getId();
            payment.setStripeSessionId(mockSessionId);
            payment.setCheckoutUrl(baseUrl + "/mock-checkout.html?session_id=" + mockSessionId + "&amount=" + amount + "&email=" + email);
            payment = paymentRepository.save(payment);
            log.info("Created MOCK checkout session {} for payment {}", mockSessionId, payment.getId());
            return payment;
        }

        // Create Stripe Checkout Session
        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setCustomerEmail(email)
            .setSuccessUrl(baseUrl + "/payment/success?session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(baseUrl + "/payment/cancel?session_id={CHECKOUT_SESSION_ID}")
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(currency != null ? currency.toLowerCase() : "eur")
                            .setUnitAmount(amountInCents)
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName("Makse")
                                    .setDescription("Makse summas " + amount + " " + (currency != null ? currency : "EUR"))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .putMetadata("payment_id", payment.getId())
            .build();

        Session session = Session.create(params);

        payment.setStripeSessionId(session.getId());
        payment.setCheckoutUrl(session.getUrl());
        payment = paymentRepository.save(payment);

        log.info("Created checkout session {} for payment {}", session.getId(), payment.getId());
        return payment;
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPayment(String paymentId) {
        return paymentRepository.findById(paymentId);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> getPaymentBySessionId(String sessionId) {
        return paymentRepository.findByStripeSessionId(sessionId);
    }

    @Transactional
    public void handleWebhook(String payload, String signature) throws SignatureVerificationException {
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            log.warn("Webhook secret not configured, skipping signature verification");
            return;
        }

        Event event = Webhook.constructEvent(payload, signature, stripeWebhookSecret);
        String eventId = event.getId();

        // Idempotency check - skip if already processed
        if (paymentEventRepository.existsByProviderEventId(eventId)) {
            log.info("Event {} already processed, skipping", eventId);
            return;
        }

        String eventType = event.getType();
        log.info("Processing webhook event: {} ({})", eventType, eventId);

        switch (eventType) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event, payload);
            case "checkout.session.expired" -> handleCheckoutExpired(event, payload);
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event, payload);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event, payload);
            default -> log.debug("Unhandled event type: {}", eventType);
        }
    }

    private void handleCheckoutCompleted(Event event, String payload) {
        Session session = (Session) event.getDataObjectDeserializer()
            .getObject().orElse(null);

        if (session == null) {
            log.error("Could not deserialize session from event");
            return;
        }

        String sessionId = session.getId();
        paymentRepository.findByStripeSessionId(sessionId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setStripePaymentIntentId(session.getPaymentIntent());
            payment.setCompletedAt(Instant.now());
            paymentRepository.save(payment);

            // Store event for idempotency
            PaymentEvent paymentEvent = new PaymentEvent(
                payment.getId(),
                event.getType(),
                event.getId(),
                payload
            );
            paymentEvent.setProcessed(true);
            paymentEventRepository.save(paymentEvent);

            log.info("Payment {} completed via session {}", payment.getId(), sessionId);
        });
    }

    private void handleCheckoutExpired(Event event, String payload) {
        Session session = (Session) event.getDataObjectDeserializer()
            .getObject().orElse(null);

        if (session == null) return;

        String sessionId = session.getId();
        paymentRepository.findByStripeSessionId(sessionId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.CANCELLED);
            paymentRepository.save(payment);

            PaymentEvent paymentEvent = new PaymentEvent(
                payment.getId(),
                event.getType(),
                event.getId(),
                payload
            );
            paymentEvent.setProcessed(true);
            paymentEventRepository.save(paymentEvent);

            log.info("Payment {} expired via session {}", payment.getId(), sessionId);
        });
    }

    private void handlePaymentIntentSucceeded(Event event, String payload) {
        // Additional confirmation, already handled via checkout.session.completed
        log.debug("Payment intent succeeded event received");
    }

    private void handlePaymentIntentFailed(Event event, String payload) {
        log.warn("Payment intent failed event received");
    }

    @Transactional
    public Payment refreshPaymentStatus(String paymentId) throws StripeException {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getStripeSessionId() == null) {
            return payment;
        }

        // In mock mode, just return current status (updated via mock endpoint)
        if (mockMode) {
            return payment;
        }

        Session session = Session.retrieve(payment.getStripeSessionId());
        String status = session.getStatus();

        switch (status) {
            case "complete" -> {
                if (payment.getStatus() != PaymentStatus.COMPLETED) {
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setStripePaymentIntentId(session.getPaymentIntent());
                    payment.setCompletedAt(Instant.now());
                    payment = paymentRepository.save(payment);
                }
            }
            case "expired" -> {
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    payment = paymentRepository.save(payment);
                }
            }
        }

        return payment;
    }

    /**
     * Complete a mock payment (for testing without Stripe).
     */
    @Transactional
    public Payment completeMockPayment(String sessionId) {
        if (!mockMode) {
            throw new IllegalStateException("Mock payments only available in mock mode");
        }

        Payment payment = paymentRepository.findByStripeSessionId(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found for session: " + sessionId));

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(Instant.now());
        payment = paymentRepository.save(payment);

        log.info("MOCK payment {} completed", payment.getId());
        return payment;
    }

    /**
     * Cancel a mock payment (for testing without Stripe).
     */
    @Transactional
    public Payment cancelMockPayment(String sessionId) {
        if (!mockMode) {
            throw new IllegalStateException("Mock payments only available in mock mode");
        }

        Payment payment = paymentRepository.findByStripeSessionId(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found for session: " + sessionId));

        payment.setStatus(PaymentStatus.CANCELLED);
        payment = paymentRepository.save(payment);

        log.info("MOCK payment {} cancelled", payment.getId());
        return payment;
    }
}
