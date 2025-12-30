package com.example.mcp.controller;

import com.example.mcp.config.GatewayConfig;
import com.example.mcp.model.*;
import com.example.mcp.service.MetricsService;
import com.example.mcp.service.ToolService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final ToolService toolService;
    private final MetricsService metricsService;
    private final GatewayConfig config;

    public ApiController(ToolService toolService, MetricsService metricsService, GatewayConfig config) {
        this.toolService = toolService;
        this.metricsService = metricsService;
        this.config = config;
    }

    @GetMapping("/tools")
    public List<ToolDef> getTools() {
        return toolService.getTools();
    }

    @PostMapping("/call")
    public CallResponse callTool(@RequestBody CallRequest request) {
        return toolService.call(request);
    }

    @GetMapping("/activity")
    public List<ActivityEntry> getActivity() {
        return metricsService.getRecentActivity();
    }

    @GetMapping("/metrics")
    public Metrics getMetrics() {
        return metricsService.getMetrics();
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
            "apiKey", config.getSecurity().getApiKey()
        ));
    }

    /**
     * Get current authenticated user info.
     * Returns user details if authenticated via Bearer token,
     * or indicates API key auth if using X-API-Key header.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");

        if (user != null) {
            // Authenticated via Bearer token
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "authenticated", true,
                "authMethod", "token",
                "user", user.toUserInfo()
            ));
        }

        // If we got here, authenticated via API key (no user context)
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) {
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "authenticated", true,
                "authMethod", "api_key",
                "user", Map.of(
                    "id", "api-key-user",
                    "email", "system@api",
                    "role", "api"
                )
            ));
        }

        // Should not reach here due to filter, but just in case
        return ResponseEntity.status(401)
            .body(Map.of("error", "Not authenticated", "code", "UNAUTHORIZED"));
    }
}
