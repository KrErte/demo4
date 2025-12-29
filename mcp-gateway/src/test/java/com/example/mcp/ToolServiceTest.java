package com.example.mcp;

import com.example.mcp.config.GatewayConfig;
import com.example.mcp.model.CallRequest;
import com.example.mcp.model.CallResponse;
import com.example.mcp.service.MetricsService;
import com.example.mcp.service.ToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolServiceTest {

    private GatewayConfig config;
    private MetricsService metrics;
    private ToolService toolService;

    @BeforeEach
    void setUp() {
        config = new GatewayConfig();
        config.getPolicy().setDefaultDeny(true);
        config.getPolicy().setTimeoutMs(5000);
        config.getPolicy().setMaxResultBytes(1048576);
        config.getFs().setAllowlist(List.of("/tmp"));
        config.getWeb().setAllowDomains(List.of("httpbin.org"));
        config.getFeatures().setFsEnabled(true);
        config.getFeatures().setHttpEnabled(true);

        metrics = new MetricsService(config);
        toolService = new ToolService(config, metrics);
    }

    @Test
    @DisplayName("fs.listDir - should deny path not in allowlist")
    void fsListDir_shouldDenyPathNotInAllowlist() {
        CallRequest request = new CallRequest("fs.listDir", Map.of("path", "/etc"));
        CallResponse response = toolService.call(request);

        assertFalse(response.ok());
        assertEquals("DENIED", response.code());
        assertTrue(response.error().contains("not in allowlist"));
    }

    @Test
    @DisplayName("fs.listDir - should allow path in allowlist")
    void fsListDir_shouldAllowPathInAllowlist() {
        CallRequest request = new CallRequest("fs.listDir", Map.of("path", "/tmp"));
        CallResponse response = toolService.call(request);

        assertTrue(response.ok());
        assertNotNull(response.result());
    }

    @Test
    @DisplayName("fs.readFile - should deny path not in allowlist")
    void fsReadFile_shouldDenyPathNotInAllowlist() {
        CallRequest request = new CallRequest("fs.readFile", Map.of("path", "/etc/passwd"));
        CallResponse response = toolService.call(request);

        assertFalse(response.ok());
        assertEquals("DENIED", response.code());
    }

    @Test
    @DisplayName("web.fetch - should deny domain not in allowlist")
    void webFetch_shouldDenyDomainNotInAllowlist() {
        CallRequest request = new CallRequest("web.fetch", Map.of("url", "https://evil.com/data"));
        CallResponse response = toolService.call(request);

        assertFalse(response.ok());
        assertEquals("DENIED", response.code());
        assertTrue(response.error().contains("not in allowlist"));
    }

    @Test
    @DisplayName("unknown tool - should return INVALID error")
    void unknownTool_shouldReturnInvalid() {
        CallRequest request = new CallRequest("unknown.tool", Map.of());
        CallResponse response = toolService.call(request);

        assertFalse(response.ok());
        assertEquals("INVALID", response.code());
    }

    @Test
    @DisplayName("fs.listDir - should deny when filesystem disabled")
    void fsListDir_shouldDenyWhenDisabled() {
        config.getFeatures().setFsEnabled(false);

        CallRequest request = new CallRequest("fs.listDir", Map.of("path", "/tmp"));
        CallResponse response = toolService.call(request);

        assertFalse(response.ok());
        assertEquals("DENIED", response.code());
        assertTrue(response.error().contains("disabled"));
    }

    @Test
    @DisplayName("web.fetch - should deny when http disabled")
    void webFetch_shouldDenyWhenDisabled() {
        config.getFeatures().setHttpEnabled(false);

        CallRequest request = new CallRequest("web.fetch", Map.of("url", "https://httpbin.org/get"));
        CallResponse response = toolService.call(request);

        assertFalse(response.ok());
        assertEquals("DENIED", response.code());
        assertTrue(response.error().contains("disabled"));
    }

    @Test
    @DisplayName("should track metrics correctly")
    void shouldTrackMetrics() {
        // First call - allowed
        toolService.call(new CallRequest("fs.listDir", Map.of("path", "/tmp")));

        // Second call - denied
        toolService.call(new CallRequest("fs.listDir", Map.of("path", "/etc")));

        var metricsData = metrics.getMetrics();
        assertEquals(1, metricsData.allowedCount());
        assertEquals(1, metricsData.deniedCount());
        assertEquals(2, metricsData.totalRequests());
    }

    @Test
    @DisplayName("getTools - should return 3 tools")
    void getTools_shouldReturn3Tools() {
        var tools = toolService.getTools();
        assertEquals(3, tools.size());
        assertTrue(tools.stream().anyMatch(t -> t.name().equals("fs.listDir")));
        assertTrue(tools.stream().anyMatch(t -> t.name().equals("fs.readFile")));
        assertTrue(tools.stream().anyMatch(t -> t.name().equals("web.fetch")));
    }
}
