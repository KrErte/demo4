package com.example.mcp.oauth.controller;

import com.example.mcp.oauth.model.OAuthToken;
import com.example.mcp.oauth.service.GoogleOAuthService;
import com.example.mcp.oauth.service.GoogleOAuthService.OAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/oauth")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final GoogleOAuthService googleOAuthService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public OAuthController(GoogleOAuthService googleOAuthService) {
        this.googleOAuthService = googleOAuthService;
    }

    /**
     * Check OAuth configuration status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
            "google", Map.of(
                "configured", googleOAuthService.isConfigured()
            )
        ));
    }

    /**
     * Start Google OAuth flow
     * Returns the authorization URL to redirect the user to
     */
    @PostMapping("/google/start")
    public ResponseEntity<?> startGoogleAuth(@RequestBody(required = false) StartAuthRequest request) {
        if (!googleOAuthService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", "Google OAuth is not configured. Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET."
            ));
        }

        String email = request != null ? request.email() : null;

        try {
            var result = googleOAuthService.generateAuthUrl(email);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "authUrl", result.authUrl(),
                "state", result.state()
            ));
        } catch (Exception e) {
            log.error("Failed to start OAuth flow", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "error", "Failed to start OAuth flow: " + e.getMessage()
            ));
        }
    }

    /**
     * Google OAuth callback - handles the redirect from Google
     * Redirects to frontend with success/error status
     */
    @GetMapping("/google/callback")
    public ResponseEntity<?> googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        // Handle user denial
        if (error != null) {
            log.warn("OAuth denied by user: {}", error);
            return ResponseEntity.status(302)
                .location(URI.create(baseUrl + "/?oauth=error&reason=denied"))
                .build();
        }

        if (code == null || state == null) {
            return ResponseEntity.status(302)
                .location(URI.create(baseUrl + "/?oauth=error&reason=missing_params"))
                .build();
        }

        try {
            OAuthToken token = googleOAuthService.handleCallback(code, state);

            // Redirect to frontend with success
            String redirectUrl = baseUrl + "/?oauth=success&email=" +
                java.net.URLEncoder.encode(token.getEmail(), java.nio.charset.StandardCharsets.UTF_8);

            return ResponseEntity.status(302)
                .location(URI.create(redirectUrl))
                .build();

        } catch (OAuthException e) {
            log.error("OAuth callback failed: {}", e.getMessage());
            return ResponseEntity.status(302)
                .location(URI.create(baseUrl + "/?oauth=error&reason=" +
                    java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8)))
                .build();
        }
    }

    /**
     * Check connection status for an email
     */
    @GetMapping("/google/status/{email}")
    public ResponseEntity<?> getGoogleStatus(@PathVariable String email) {
        var token = googleOAuthService.getToken(email);

        if (token.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "connected", false,
                "email", email
            ));
        }

        OAuthToken t = token.get();
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "connected", true,
            "email", t.getEmail(),
            "valid", !t.isExpired(),
            "expiresAt", t.getExpiresAt() != null ? t.getExpiresAt().toString() : null,
            "connectedAt", t.getCreatedAt().toString()
        ));
    }

    /**
     * Disconnect Google account
     */
    @DeleteMapping("/google/disconnect/{email}")
    public ResponseEntity<?> disconnectGoogle(@PathVariable String email) {
        googleOAuthService.disconnect(email);
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "message", "Google account disconnected"
        ));
    }

    /**
     * Refresh token for an email
     */
    @PostMapping("/google/refresh/{email}")
    public ResponseEntity<?> refreshToken(@PathVariable String email) {
        try {
            OAuthToken token = googleOAuthService.refreshToken(email);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "email", token.getEmail(),
                "expiresAt", token.getExpiresAt() != null ? token.getExpiresAt().toString() : null
            ));
        } catch (OAuthException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "error", e.getMessage()
            ));
        }
    }

    // Request/Response DTOs
    public record StartAuthRequest(String email) {}
}
