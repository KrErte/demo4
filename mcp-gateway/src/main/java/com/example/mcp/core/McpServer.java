package com.example.mcp.core;

import com.example.mcp.config.McpConfig;
import com.example.mcp.model.ToolDefinition;
import com.example.mcp.model.ToolInvocation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP Server implementation supporting stdio and HTTP transports.
 */
@Component
public class McpServer {

    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);
    private static final String JSONRPC_VERSION = "2.0";

    private final McpConfig config;
    private final ToolRegistry toolRegistry;
    private final McpRequestHandler requestHandler;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread stdioThread;

    public McpServer(McpConfig config, ToolRegistry toolRegistry, McpRequestHandler requestHandler) {
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.requestHandler = requestHandler;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void start() {
        logger.info("Starting MCP Server: {} v{}", config.getServer().getName(), config.getServer().getVersion());

        if (config.getServer().isStdioEnabled()) {
            startStdioTransport();
        }

        logger.info("MCP Server started with {} tools registered", toolRegistry.size());
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (stdioThread != null) {
            stdioThread.interrupt();
        }
        requestHandler.shutdown();
        logger.info("MCP Server stopped");
    }

    /**
     * Start the stdio transport for MCP.
     */
    private void startStdioTransport() {
        running.set(true);
        stdioThread = new Thread(this::runStdioLoop, "mcp-stdio");
        stdioThread.setDaemon(true);
        stdioThread.start();
        logger.info("Stdio transport started");
    }

    /**
     * Main loop for stdio transport.
     */
    private void runStdioLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {

            while (running.get()) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    JsonNode request = objectMapper.readTree(line);
                    JsonNode response = handleJsonRpcRequest(request);
                    writer.println(objectMapper.writeValueAsString(response));
                } catch (Exception e) {
                    logger.error("Error processing request", e);
                    JsonNode errorResponse = createErrorResponse(null, -32700, "Parse error: " + e.getMessage());
                    writer.println(objectMapper.writeValueAsString(errorResponse));
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                logger.error("Stdio transport error", e);
            }
        }
    }

    /**
     * Handle a JSON-RPC request.
     */
    public JsonNode handleJsonRpcRequest(JsonNode request) {
        String method = request.path("method").asText();
        JsonNode params = request.path("params");
        JsonNode id = request.path("id");

        try {
            Object result = switch (method) {
                case "initialize" -> handleInitialize(params);
                case "tools/list" -> handleListTools();
                case "tools/call" -> handleCallTool(params);
                case "ping" -> handlePing();
                default -> throw new McpException("Unknown method: " + method, "METHOD_NOT_FOUND");
            };
            return createSuccessResponse(id, result);
        } catch (McpException e) {
            return createErrorResponse(id, mapErrorCode(e.getErrorCode()), e.getMessage());
        } catch (Exception e) {
            logger.error("Request handling error", e);
            return createErrorResponse(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Handle initialize request.
     */
    private Map<String, Object> handleInitialize(JsonNode params) {
        logger.info("Initialize request received");
        return Map.of(
            "protocolVersion", "2024-11-05",
            "serverInfo", Map.of(
                "name", config.getServer().getName(),
                "version", config.getServer().getVersion()
            ),
            "capabilities", Map.of(
                "tools", Map.of()
            )
        );
    }

    /**
     * Handle tools/list request.
     */
    private Map<String, Object> handleListTools() {
        ArrayNode tools = objectMapper.createArrayNode();
        for (ToolDefinition tool : toolRegistry.getAll()) {
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("name", tool.getName());
            toolNode.put("description", tool.getDescription());
            toolNode.set("inputSchema", tool.getInputSchema());
            tools.add(toolNode);
        }
        return Map.of("tools", tools);
    }

    /**
     * Handle tools/call request.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> handleCallTool(JsonNode params) {
        String toolName = params.path("name").asText();
        Map<String, Object> arguments = objectMapper.convertValue(
            params.path("arguments"),
            Map.class
        );

        ToolInvocation invocation = new ToolInvocation(toolName, arguments);
        Object result = requestHandler.handleInvocation(invocation);

        return Map.of(
            "content", java.util.List.of(
                Map.of(
                    "type", "text",
                    "text", objectMapper.valueToTree(result).toString()
                )
            )
        );
    }

    /**
     * Handle ping request.
     */
    private Map<String, Object> handlePing() {
        return Map.of("status", "ok");
    }

    /**
     * Create a JSON-RPC success response.
     */
    private JsonNode createSuccessResponse(JsonNode id, Object result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSONRPC_VERSION);
        if (id != null && !id.isMissingNode()) {
            response.set("id", id);
        }
        response.set("result", objectMapper.valueToTree(result));
        return response;
    }

    /**
     * Create a JSON-RPC error response.
     */
    private JsonNode createErrorResponse(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSONRPC_VERSION);
        if (id != null && !id.isMissingNode()) {
            response.set("id", id);
        }
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        return response;
    }

    /**
     * Map internal error codes to JSON-RPC error codes.
     */
    private int mapErrorCode(String errorCode) {
        return switch (errorCode) {
            case "TOOL_NOT_FOUND" -> -32601;
            case "VALIDATION_FAILED" -> -32602;
            case "POLICY_DENIED" -> -32001;
            case "TIMEOUT" -> -32002;
            case "RESULT_TOO_LARGE" -> -32003;
            default -> -32603;
        };
    }

    public boolean isRunning() {
        return running.get();
    }
}
