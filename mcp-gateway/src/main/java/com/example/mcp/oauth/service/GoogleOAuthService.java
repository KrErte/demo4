package com.example.mcp.oauth.service;

import com.example.mcp.oauth.config.GoogleOAuthConfig;
import com.example.mcp.oauth.model.OAuthToken;
import com.example.mcp.oauth.repository.OAuthTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);
    private static final String PROVIDER = "google";
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    private final GoogleOAuthConfig config;
    private final OAuthTokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // In-memory state storage for OAuth flow (state -> email mapping)
    private final Map<String, OAuthState> pendingStates = new ConcurrentHashMap<>();

    public GoogleOAuthService(GoogleOAuthConfig config, OAuthTokenRepository tokenRepository, ObjectMapper objectMapper) {
        this.config = config;
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    /**
     * Generate the OAuth authorization URL for the user to visit
     */
    public AuthUrlResult generateAuthUrl(String userEmail) {
        if (!isConfigured()) {
            throw new IllegalStateException("Google OAuth is not configured");
        }

        String state = UUID.randomUUID().toString();
        pendingStates.put(state, new OAuthState(userEmail, Instant.now()));

        // Clean up old states (older than 10 minutes)
        cleanupOldStates();

        String scopeString = String.join(" ", config.getScopes());

        String authUrl = AUTH_URL + "?" +
            "client_id=" + urlEncode(config.getClientId()) +
            "&redirect_uri=" + urlEncode(config.getRedirectUri()) +
            "&response_type=code" +
            "&scope=" + urlEncode(scopeString) +
            "&state=" + urlEncode(state) +
            "&access_type=offline" +
            "&prompt=consent";

        return new AuthUrlResult(authUrl, state);
    }

    /**
     * Handle the OAuth callback and exchange code for tokens
     */
    @Transactional
    public OAuthToken handleCallback(String code, String state) throws OAuthException {
        if (!isConfigured()) {
            throw new OAuthException("Google OAuth is not configured");
        }

        OAuthState pendingState = pendingStates.remove(state);
        if (pendingState == null) {
            throw new OAuthException("Invalid or expired state parameter");
        }

        // Check if state is too old (10 minutes)
        if (pendingState.createdAt.plusSeconds(600).isBefore(Instant.now())) {
            throw new OAuthException("OAuth state has expired");
        }

        try {
            // Exchange code for tokens
            TokenResponse tokenResponse = exchangeCodeForTokens(code);

            // Get user info
            UserInfo userInfo = getUserInfo(tokenResponse.accessToken);

            // Validate email matches (if we had a specific email)
            String email = userInfo.email;

            // Save or update token
            OAuthToken token = tokenRepository.findByEmailAndProvider(email, PROVIDER)
                .orElse(new OAuthToken(email, PROVIDER));

            token.setAccessToken(tokenResponse.accessToken);
            token.setRefreshToken(tokenResponse.refreshToken);
            token.setTokenType(tokenResponse.tokenType);
            token.setScopes(String.join(" ", config.getScopes()));

            if (tokenResponse.expiresIn != null) {
                token.setExpiresAt(Instant.now().plusSeconds(tokenResponse.expiresIn));
            }

            token.setUserInfoJson(objectMapper.writeValueAsString(userInfo));

            tokenRepository.save(token);

            log.info("Successfully connected Google account for: {}", email);
            return token;

        } catch (IOException | InterruptedException e) {
            log.error("Failed to exchange OAuth code", e);
            throw new OAuthException("Failed to complete OAuth flow: " + e.getMessage());
        }
    }

    /**
     * Get existing token for an email
     */
    public Optional<OAuthToken> getToken(String email) {
        return tokenRepository.findByEmailAndProvider(email, PROVIDER);
    }

    /**
     * Check if an email has a valid (non-expired) token
     */
    public boolean hasValidToken(String email) {
        return tokenRepository.findByEmailAndProvider(email, PROVIDER)
            .map(token -> !token.isExpired())
            .orElse(false);
    }

    /**
     * Refresh an expired token
     */
    @Transactional
    public OAuthToken refreshToken(String email) throws OAuthException {
        OAuthToken token = tokenRepository.findByEmailAndProvider(email, PROVIDER)
            .orElseThrow(() -> new OAuthException("No token found for email: " + email));

        if (token.getRefreshToken() == null) {
            throw new OAuthException("No refresh token available");
        }

        try {
            String body = "client_id=" + urlEncode(config.getClientId()) +
                "&client_secret=" + urlEncode(config.getClientSecret()) +
                "&refresh_token=" + urlEncode(token.getRefreshToken()) +
                "&grant_type=refresh_token";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Token refresh failed: {}", response.body());
                throw new OAuthException("Failed to refresh token");
            }

            JsonNode json = objectMapper.readTree(response.body());
            token.setAccessToken(json.get("access_token").asText());

            if (json.has("expires_in")) {
                token.setExpiresAt(Instant.now().plusSeconds(json.get("expires_in").asLong()));
            }

            // Refresh token is not returned on refresh, keep the existing one

            tokenRepository.save(token);
            log.info("Successfully refreshed token for: {}", email);

            return token;

        } catch (IOException | InterruptedException e) {
            log.error("Failed to refresh token", e);
            throw new OAuthException("Failed to refresh token: " + e.getMessage());
        }
    }

    /**
     * Disconnect Google account
     */
    @Transactional
    public void disconnect(String email) {
        tokenRepository.deleteByEmailAndProvider(email, PROVIDER);
        log.info("Disconnected Google account for: {}", email);
    }

    /**
     * Get a valid access token, refreshing if necessary
     */
    public String getValidAccessToken(String email) throws OAuthException {
        OAuthToken token = tokenRepository.findByEmailAndProvider(email, PROVIDER)
            .orElseThrow(() -> new OAuthException("No token found for email: " + email));

        if (token.isExpired() && token.getRefreshToken() != null) {
            token = refreshToken(email);
        }

        if (token.isExpired()) {
            throw new OAuthException("Token is expired and cannot be refreshed");
        }

        return token.getAccessToken();
    }

    private TokenResponse exchangeCodeForTokens(String code) throws IOException, InterruptedException {
        String body = "client_id=" + urlEncode(config.getClientId()) +
            "&client_secret=" + urlEncode(config.getClientSecret()) +
            "&code=" + urlEncode(code) +
            "&redirect_uri=" + urlEncode(config.getRedirectUri()) +
            "&grant_type=authorization_code";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Token exchange failed: {}", response.body());
            throw new IOException("Token exchange failed: " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());

        return new TokenResponse(
            json.get("access_token").asText(),
            json.has("refresh_token") ? json.get("refresh_token").asText() : null,
            json.has("token_type") ? json.get("token_type").asText() : "Bearer",
            json.has("expires_in") ? json.get("expires_in").asLong() : null
        );
    }

    private UserInfo getUserInfo(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(USERINFO_URL))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to get user info: " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());

        return new UserInfo(
            json.has("id") ? json.get("id").asText() : null,
            json.has("email") ? json.get("email").asText() : null,
            json.has("name") ? json.get("name").asText() : null,
            json.has("picture") ? json.get("picture").asText() : null
        );
    }

    private void cleanupOldStates() {
        Instant cutoff = Instant.now().minusSeconds(600); // 10 minutes
        pendingStates.entrySet().removeIf(entry -> entry.getValue().createdAt.isBefore(cutoff));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // Inner classes for data transfer
    public record AuthUrlResult(String authUrl, String state) {}
    private record TokenResponse(String accessToken, String refreshToken, String tokenType, Long expiresIn) {}
    public record UserInfo(String id, String email, String name, String picture) {}
    private record OAuthState(String email, Instant createdAt) {}

    public static class OAuthException extends Exception {
        public OAuthException(String message) {
            super(message);
        }
    }
}
