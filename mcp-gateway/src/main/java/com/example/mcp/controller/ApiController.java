package com.example.mcp.controller;

import com.example.mcp.config.GatewayConfig;
import com.example.mcp.model.*;
import com.example.mcp.service.MetricsService;
import com.example.mcp.service.ToolService;
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
}
