package com.example.mcp.model;

/**
 * Login request payload.
 */
public record LoginRequest(
    String email,
    String password
) {}
