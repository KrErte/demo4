package com.example.mcp.oauth.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_token")
public class OAuthToken {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_type", length = 32)
    private String tokenType;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(columnDefinition = "TEXT")
    private String scopes;

    @Column(name = "user_info_json", columnDefinition = "TEXT")
    private String userInfoJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OAuthToken() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public OAuthToken(String email, String provider) {
        this();
        this.email = email;
        this.provider = provider;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }

    public String getUserInfoJson() { return userInfoJson; }
    public void setUserInfoJson(String userInfoJson) { this.userInfoJson = userInfoJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public OAuthTokenDto toDto() {
        return new OAuthTokenDto(
            id, email, provider, tokenType,
            expiresAt, scopes, createdAt, updatedAt,
            !isExpired()
        );
    }

    public record OAuthTokenDto(
        String id,
        String email,
        String provider,
        String tokenType,
        Instant expiresAt,
        String scopes,
        Instant createdAt,
        Instant updatedAt,
        boolean valid
    ) {}
}
