package com.example.mcp.controller;

import com.example.mcp.model.LoginRequest;
import com.example.mcp.model.LoginResponse;
import com.example.mcp.model.User;
import com.example.mcp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication controller for login, logout, and current user info.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Login endpoint - authenticates user and returns token.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        if (request.email() == null || request.password() == null) {
            return ResponseEntity.badRequest()
                .body(LoginResponse.error("Email and password are required"));
        }

        return userService.authenticate(request.email(), request.password())
            .map(token -> {
                User user = userService.findByEmail(request.email()).orElseThrow();
                return ResponseEntity.ok(LoginResponse.success(token, user.toUserInfo()));
            })
            .orElseGet(() -> ResponseEntity.status(401)
                .body(LoginResponse.error("Invalid email or password")));
    }

    /**
     * Logout endpoint - invalidates token.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            userService.invalidateToken(token);
        }
        return ResponseEntity.ok(Map.of("ok", true, "message", "Logged out"));
    }

    /**
     * Get current user info.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");

        if (user == null) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Not authenticated", "code", "UNAUTHORIZED"));
        }

        return ResponseEntity.ok(Map.of(
            "ok", true,
            "user", user.toUserInfo()
        ));
    }

    /**
     * Extract Bearer token from Authorization header.
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
