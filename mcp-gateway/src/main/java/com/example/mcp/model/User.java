package com.example.mcp.model;

/**
 * User model for authentication.
 */
public class User {
    private final String id;
    private final String email;
    private final String passwordHash;
    private final String role;

    public User(String id, String email, String passwordHash, String role) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    /**
     * Returns a safe representation without password hash.
     */
    public UserInfo toUserInfo() {
        return new UserInfo(id, email, role);
    }

    /**
     * Safe user info for API responses (no password hash).
     */
    public record UserInfo(String id, String email, String role) {}
}
