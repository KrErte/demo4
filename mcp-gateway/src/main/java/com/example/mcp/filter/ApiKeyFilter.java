package com.example.mcp.filter;

import com.example.mcp.config.GatewayConfig;
import com.example.mcp.model.User;
import com.example.mcp.service.UserService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Authentication filter supporting both API key and Bearer token authentication.
 */
@Component
@Order(1)
public class ApiKeyFilter implements Filter {

    private final GatewayConfig config;
    private final UserService userService;

    public ApiKeyFilter(GatewayConfig config, UserService userService) {
        this.config = config;
        this.userService = userService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Public endpoints that don't require authentication
        if (isPublicEndpoint(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Only protect /api/* endpoints
        if (path.startsWith("/api/")) {
            // Try API key authentication first (backward compatibility)
            String apiKey = httpRequest.getHeader("X-API-Key");
            String expectedKey = config.getSecurity().getApiKey();

            if (apiKey != null && apiKey.equals(expectedKey)) {
                // API key is valid - allow request
                chain.doFilter(request, response);
                return;
            }

            // Try Bearer token authentication
            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                Optional<User> userOpt = userService.getUserByToken(token);

                if (userOpt.isPresent()) {
                    // Token is valid - set current user and allow request
                    httpRequest.setAttribute("currentUser", userOpt.get());
                    chain.doFilter(request, response);
                    return;
                }
            }

            // No valid authentication found
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Invalid or missing authentication\",\"code\":\"UNAUTHORIZED\"}");
            return;
        }

        // Non-API endpoints pass through
        chain.doFilter(request, response);
    }

    /**
     * Check if the endpoint is public (no auth required).
     */
    private boolean isPublicEndpoint(String path) {
        return path.equals("/api/config") ||
               path.equals("/api/auth/login") ||
               path.startsWith("/api/auth/login") ||
               path.equals("/api/payments/webhook") ||
               path.startsWith("/payment/") ||
               // OAuth endpoints
               path.equals("/api/oauth/status") ||
               path.startsWith("/api/oauth/google/");
    }
}
