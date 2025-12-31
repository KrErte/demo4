package com.example.mcp.payment.controller;

import com.example.mcp.payment.model.Payment;
import com.example.mcp.payment.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Create a new checkout session.
     * POST /api/payments/checkout
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(@RequestBody CheckoutRequest request) {
        // Validation
        if (request.email() == null || request.email().isBlank()) {
            return badRequest("E-posti aadress on kohustuslik");
        }
        if (!request.email().matches("^[^@]+@[^@]+\\.[^@]+$")) {
            return badRequest("Vigane e-posti aadress");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return badRequest("Summa peab olema suurem kui 0");
        }
        if (request.amount().compareTo(new BigDecimal("999999.99")) > 0) {
            return badRequest("Summa on liiga suur");
        }

        try {
            Payment payment = paymentService.createCheckoutSession(
                request.email(),
                request.amount(),
                request.currency()
            );

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "paymentId", payment.getId(),
                "checkoutUrl", payment.getCheckoutUrl(),
                "status", payment.getStatus().name()
            ));
        } catch (IllegalStateException e) {
            log.error("Stripe not configured", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "ok", false,
                    "error", "Maksesüsteem ei ole hetkel saadaval",
                    "code", "PAYMENT_UNAVAILABLE"
                ));
        } catch (StripeException e) {
            log.error("Stripe error creating checkout", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                    "ok", false,
                    "error", "Makselingi loomine ebaõnnestus. Palun proovige uuesti.",
                    "code", "STRIPE_ERROR"
                ));
        }
    }

    /**
     * Get payment status.
     * GET /api/payments/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getPayment(@PathVariable String id) {
        return paymentService.getPayment(id)
            .map(payment -> ResponseEntity.ok(Map.of(
                "ok", true,
                "payment", payment.toDto()
            )))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "ok", false,
                    "error", "Makset ei leitud",
                    "code", "NOT_FOUND"
                )));
    }

    /**
     * Get payment status by session ID (for success/cancel redirects).
     * GET /api/payments/session/{sessionId}
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<?> getPaymentBySession(@PathVariable String sessionId) {
        return paymentService.getPaymentBySessionId(sessionId)
            .map(payment -> ResponseEntity.ok(Map.of(
                "ok", true,
                "payment", payment.toDto()
            )))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "ok", false,
                    "error", "Makset ei leitud",
                    "code", "NOT_FOUND"
                )));
    }

    /**
     * Refresh payment status from Stripe (polling endpoint).
     * POST /api/payments/{id}/refresh
     */
    @PostMapping("/{id}/refresh")
    public ResponseEntity<?> refreshPayment(@PathVariable String id) {
        try {
            Payment payment = paymentService.refreshPaymentStatus(id);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "payment", payment.toDto()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "ok", false,
                    "error", "Makset ei leitud",
                    "code", "NOT_FOUND"
                ));
        } catch (StripeException e) {
            log.error("Stripe error refreshing payment", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                    "ok", false,
                    "error", "Makse staatuse uuendamine ebaõnnestus",
                    "code", "STRIPE_ERROR"
                ));
        }
    }

    /**
     * Stripe webhook endpoint.
     * POST /api/payments/webhook
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        if (signature == null || signature.isBlank()) {
            log.warn("Webhook received without signature");
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Missing signature"));
        }

        try {
            paymentService.handleWebhook(payload, signature);
            return ResponseEntity.ok(Map.of("received", true));
        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid signature"));
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Webhook processing failed"));
        }
    }

    /**
     * Complete a mock payment (only in mock mode).
     * POST /api/payments/mock/complete
     */
    @PostMapping("/mock/complete")
    public ResponseEntity<?> completeMockPayment(@RequestBody MockPaymentRequest request) {
        try {
            Payment payment = paymentService.completeMockPayment(request.sessionId());
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "payment", payment.toDto()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("ok", false, "error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /**
     * Cancel a mock payment (only in mock mode).
     * POST /api/payments/mock/cancel
     */
    @PostMapping("/mock/cancel")
    public ResponseEntity<?> cancelMockPayment(@RequestBody MockPaymentRequest request) {
        try {
            Payment payment = paymentService.cancelMockPayment(request.sessionId());
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "payment", payment.toDto()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("ok", false, "error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest()
            .body(Map.of(
                "ok", false,
                "error", message,
                "code", "VALIDATION_ERROR"
            ));
    }

    public record CheckoutRequest(
        String email,
        BigDecimal amount,
        String currency
    ) {}

    public record MockPaymentRequest(String sessionId) {}
}
