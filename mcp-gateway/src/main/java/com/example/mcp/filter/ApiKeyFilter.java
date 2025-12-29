package com.example.mcp.filter;

import com.example.mcp.config.GatewayConfig;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class ApiKeyFilter implements Filter {

    private final GatewayConfig config;

    public ApiKeyFilter(GatewayConfig config) {
        this.config = config;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // Only protect /api/* endpoints (except /api/config for dashboard bootstrap)
        if (path.startsWith("/api/") && !path.equals("/api/config")) {
            String apiKey = httpRequest.getHeader("X-API-Key");
            String expectedKey = config.getSecurity().getApiKey();

            if (apiKey == null || !apiKey.equals(expectedKey)) {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Invalid or missing API key\",\"code\":\"UNAUTHORIZED\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
