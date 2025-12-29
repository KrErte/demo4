package com.example.mcp;

import com.example.mcp.core.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Enterprise MCP Gateway Application.
 *
 * Supports both stdio and HTTP transports for MCP protocol.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        logger.info("Starting Enterprise MCP Gateway");
        SpringApplication.run(Application.class, args);
    }

    /**
     * HTTP Controller for MCP requests.
     */
    @RestController
    @RequestMapping("/mcp")
    public static class McpHttpController {

        private final McpServer mcpServer;

        public McpHttpController(McpServer mcpServer) {
            this.mcpServer = mcpServer;
        }

        /**
         * Handle MCP JSON-RPC requests over HTTP.
         */
        @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<JsonNode> handleRequest(@RequestBody JsonNode request) {
            JsonNode response = mcpServer.handleJsonRpcRequest(request);
            return ResponseEntity.ok(response);
        }

        /**
         * Health check endpoint.
         */
        @GetMapping("/health")
        public ResponseEntity<String> health() {
            return ResponseEntity.ok("{\"status\":\"healthy\"}");
        }

        /**
         * Server info endpoint.
         */
        @GetMapping("/info")
        public ResponseEntity<String> info() {
            return ResponseEntity.ok("{\"name\":\"mcp-gateway\",\"version\":\"1.0.0\",\"protocol\":\"MCP 2024-11-05\"}");
        }
    }
}
