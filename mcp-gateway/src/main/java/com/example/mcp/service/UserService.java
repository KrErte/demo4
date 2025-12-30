package com.example.mcp.service;

import com.example.mcp.model.User;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User service with in-memory user store and token management.
 */
@Service
public class UserService {

    // In-memory user store (keyed by email)
    private final Map<String, User> users = new ConcurrentHashMap<>();

    // Token store (token -> user)
    private final Map<String, User> tokens = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Create default demo users
        createUser("1", "admin@pilvetark.ee", "admin123", "admin");
        createUser("2", "user@pilvetark.ee", "user123", "user");
        createUser("3", "demo@pilvetark.ee", "demo", "user");
    }

    /**
     * Create a user with hashed password.
     */
    public User createUser(String id, String email, String password, String role) {
        String passwordHash = hashPassword(password);
        User user = new User(id, email, passwordHash, role);
        users.put(email.toLowerCase(), user);
        return user;
    }

    /**
     * Find user by email.
     */
    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return Optional.ofNullable(users.get(email.toLowerCase()));
    }

    /**
     * Verify password against stored hash.
     */
    public boolean verifyPassword(User user, String password) {
        if (user == null || password == null) return false;
        String hash = hashPassword(password);
        return hash.equals(user.getPasswordHash());
    }

    /**
     * Authenticate user and return token if successful.
     */
    public Optional<String> authenticate(String email, String password) {
        return findByEmail(email)
            .filter(user -> verifyPassword(user, password))
            .map(user -> {
                String token = generateToken();
                tokens.put(token, user);
                return token;
            });
    }

    /**
     * Get user by token.
     */
    public Optional<User> getUserByToken(String token) {
        if (token == null) return Optional.empty();
        return Optional.ofNullable(tokens.get(token));
    }

    /**
     * Invalidate a token (logout).
     */
    public void invalidateToken(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    /**
     * Get all users (for admin purposes).
     */
    public List<User.UserInfo> getAllUsers() {
        return users.values().stream()
            .map(User::toUserInfo)
            .toList();
    }

    /**
     * Simple password hashing using SHA-256.
     * In production, use BCrypt or Argon2.
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Generate a random token.
     */
    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}
