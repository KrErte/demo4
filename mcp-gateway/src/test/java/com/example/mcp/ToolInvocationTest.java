package com.example.mcp;

import com.example.mcp.config.PolicyConfig;
import com.example.mcp.core.*;
import com.example.mcp.model.ToolDefinition;
import com.example.mcp.model.ToolInvocation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for tool invocation.
 */
class ToolInvocationTest {

    private ToolRegistry toolRegistry;
    private PolicyConfig policyConfig;
    private PolicyEngine policyEngine;
    private AuditService auditService;
    private McpRequestHandler requestHandler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        policyConfig = new PolicyConfig();
        policyConfig.setDefaultDeny(true);
        policyConfig.setDefaultTimeoutMs(5000);
        policyConfig.setDefaultMaxResultBytes(1048576);
        policyConfig.setAllowTools(List.of("test.echo", "test.slow", "test.large", "test.error"));

        policyEngine = new PolicyEngine(policyConfig);
        auditService = new AuditService();
        requestHandler = new McpRequestHandler(toolRegistry, policyEngine, auditService);
        objectMapper = new ObjectMapper();

        registerTestTools();
    }

    private void registerTestTools() {
        // Echo tool - returns input
        ObjectNode echoSchema = objectMapper.createObjectNode();
        echoSchema.put("type", "object");
        ObjectNode props = echoSchema.putObject("properties");
        props.putObject("message").put("type", "string");
        echoSchema.putArray("required").add("message");

        toolRegistry.register(new ToolDefinition(
            "test.echo",
            "Echo back the input message",
            echoSchema,
            args -> Map.of("echo", args.get("message"))
        ));

        // Slow tool - takes time to execute
        ObjectNode slowSchema = objectMapper.createObjectNode();
        slowSchema.put("type", "object");
        slowSchema.putObject("properties").putObject("delayMs").put("type", "integer");

        toolRegistry.register(new ToolDefinition(
            "test.slow",
            "Tool that takes time to execute",
            slowSchema,
            args -> {
                int delay = (Integer) args.getOrDefault("delayMs", 100);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Map.of("completed", true, "delay", delay);
            }
        ));

        // Large result tool
        ObjectNode largeSchema = objectMapper.createObjectNode();
        largeSchema.put("type", "object");
        largeSchema.putObject("properties").putObject("sizeKb").put("type", "integer");

        toolRegistry.register(new ToolDefinition(
            "test.large",
            "Tool that returns a large result",
            largeSchema,
            args -> {
                int sizeKb = (Integer) args.getOrDefault("sizeKb", 1);
                String data = "x".repeat(sizeKb * 1024);
                return Map.of("data", data, "size", data.length());
            }
        ));

        // Error tool
        ObjectNode errorSchema = objectMapper.createObjectNode();
        errorSchema.put("type", "object");

        toolRegistry.register(new ToolDefinition(
            "test.error",
            "Tool that throws an error",
            errorSchema,
            args -> {
                throw new RuntimeException("Intentional error for testing");
            }
        ));
    }

    @Nested
    @DisplayName("Successful Invocation Tests")
    class SuccessfulInvocationTests {

        @Test
        @DisplayName("Should successfully invoke echo tool")
        void shouldInvokeEchoTool() {
            ToolInvocation invocation = new ToolInvocation("test.echo", Map.of("message", "Hello, World!"));
            Object result = requestHandler.handleInvocation(invocation);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals("Hello, World!", resultMap.get("echo"));
        }

        @Test
        @DisplayName("Should handle tool with execution time")
        void shouldHandleSlowTool() {
            ToolInvocation invocation = new ToolInvocation("test.slow", Map.of("delayMs", 100));
            Object result = requestHandler.handleInvocation(invocation);

            assertNotNull(result);
            assertTrue(result instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertEquals(true, resultMap.get("completed"));
        }
    }

    @Nested
    @DisplayName("Policy Enforcement Tests")
    class PolicyEnforcementTests {

        @Test
        @DisplayName("Should deny tool not in allow list")
        void shouldDenyUnknownTool() {
            ToolInvocation invocation = new ToolInvocation("unknown.tool", Map.of());

            McpException exception = assertThrows(McpException.class, () ->
                requestHandler.handleInvocation(invocation));
            assertEquals("TOOL_NOT_FOUND", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should deny tool in deny list")
        void shouldDenyBlockedTool() {
            policyConfig.setDenyTools(List.of("test.echo"));

            ToolInvocation invocation = new ToolInvocation("test.echo", Map.of("message", "test"));

            McpException exception = assertThrows(McpException.class, () ->
                requestHandler.handleInvocation(invocation));
            assertTrue(exception.isPolicyViolation());
        }
    }

    @Nested
    @DisplayName("Timeout Enforcement Tests")
    class TimeoutEnforcementTests {

        @Test
        @DisplayName("Should timeout slow tool execution")
        void shouldTimeoutSlowTool() {
            // Set a very short timeout
            PolicyConfig.ToolPolicy toolPolicy = new PolicyConfig.ToolPolicy();
            toolPolicy.setAllow(true);
            toolPolicy.setTimeoutMs(50); // 50ms timeout
            toolPolicy.setMaxResultBytes(1048576);
            policyConfig.setPerTool(Map.of("test.slow", toolPolicy));

            // Reinitialize with new policy
            policyEngine = new PolicyEngine(policyConfig);
            requestHandler = new McpRequestHandler(toolRegistry, policyEngine, auditService);

            ToolInvocation invocation = new ToolInvocation("test.slow", Map.of("delayMs", 1000));

            McpException exception = assertThrows(McpException.class, () ->
                requestHandler.handleInvocation(invocation));
            assertEquals("TIMEOUT", exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Result Size Enforcement Tests")
    class ResultSizeEnforcementTests {

        @Test
        @DisplayName("Should reject result exceeding max size")
        void shouldRejectLargeResult() {
            // Set a small max result size
            PolicyConfig.ToolPolicy toolPolicy = new PolicyConfig.ToolPolicy();
            toolPolicy.setAllow(true);
            toolPolicy.setTimeoutMs(5000);
            toolPolicy.setMaxResultBytes(1024); // 1KB max
            policyConfig.setPerTool(Map.of("test.large", toolPolicy));

            // Reinitialize with new policy
            policyEngine = new PolicyEngine(policyConfig);
            requestHandler = new McpRequestHandler(toolRegistry, policyEngine, auditService);

            ToolInvocation invocation = new ToolInvocation("test.large", Map.of("sizeKb", 10)); // 10KB result

            McpException exception = assertThrows(McpException.class, () ->
                requestHandler.handleInvocation(invocation));
            assertEquals("RESULT_TOO_LARGE", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should allow result within max size")
        void shouldAllowSmallResult() {
            ToolInvocation invocation = new ToolInvocation("test.large", Map.of("sizeKb", 1));
            Object result = requestHandler.handleInvocation(invocation);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle tool execution error")
        void shouldHandleToolError() {
            ToolInvocation invocation = new ToolInvocation("test.error", Map.of());

            McpException exception = assertThrows(McpException.class, () ->
                requestHandler.handleInvocation(invocation));
            assertEquals("EXECUTION_ERROR", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("Intentional error"));
        }
    }

    @Nested
    @DisplayName("Schema Validation Tests")
    class SchemaValidationTests {

        @Test
        @DisplayName("Should reject invocation with missing required argument")
        void shouldRejectMissingRequiredArg() {
            ToolInvocation invocation = new ToolInvocation("test.echo", Map.of()); // Missing 'message'

            McpException exception = assertThrows(McpException.class, () ->
                requestHandler.handleInvocation(invocation));
            assertEquals("VALIDATION_FAILED", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should accept invocation with valid arguments")
        void shouldAcceptValidArgs() {
            ToolInvocation invocation = new ToolInvocation("test.echo", Map.of("message", "valid"));
            Object result = requestHandler.handleInvocation(invocation);
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("Tool Registry Tests")
    class ToolRegistryTests {

        @Test
        @DisplayName("Should register and retrieve tools")
        void shouldRegisterAndRetrieveTool() {
            assertTrue(toolRegistry.exists("test.echo"));
            assertTrue(toolRegistry.exists("test.slow"));
            assertFalse(toolRegistry.exists("nonexistent.tool"));
        }

        @Test
        @DisplayName("Should list all registered tools")
        void shouldListAllTools() {
            assertEquals(4, toolRegistry.size());
            assertTrue(toolRegistry.getToolNames().contains("test.echo"));
            assertTrue(toolRegistry.getToolNames().contains("test.slow"));
        }

        @Test
        @DisplayName("Should unregister tool")
        void shouldUnregisterTool() {
            toolRegistry.unregister("test.echo");
            assertFalse(toolRegistry.exists("test.echo"));
            assertEquals(3, toolRegistry.size());
        }
    }

    @Nested
    @DisplayName("Audit Service Tests")
    class AuditServiceTests {

        @Test
        @DisplayName("Should hash arguments consistently")
        void shouldHashArgumentsConsistently() {
            Map<String, Object> args1 = Map.of("key", "value");
            Map<String, Object> args2 = Map.of("key", "value");
            Map<String, Object> args3 = Map.of("key", "different");

            String hash1 = auditService.hashArguments(args1);
            String hash2 = auditService.hashArguments(args2);
            String hash3 = auditService.hashArguments(args3);

            assertEquals(hash1, hash2);
            assertNotEquals(hash1, hash3);
        }

        @Test
        @DisplayName("Should handle empty arguments")
        void shouldHandleEmptyArguments() {
            String hash = auditService.hashArguments(Map.of());
            assertEquals("empty", hash);

            String nullHash = auditService.hashArguments(null);
            assertEquals("empty", nullHash);
        }
    }

    @Nested
    @DisplayName("Invocation Model Tests")
    class InvocationModelTests {

        @Test
        @DisplayName("Should create invocation with unique ID")
        void shouldCreateInvocationWithUniqueId() {
            ToolInvocation inv1 = new ToolInvocation("test.tool", Map.of());
            ToolInvocation inv2 = new ToolInvocation("test.tool", Map.of());

            assertNotNull(inv1.getId());
            assertNotNull(inv2.getId());
            assertNotEquals(inv1.getId(), inv2.getId());
        }

        @Test
        @DisplayName("Should set default actor")
        void shouldSetDefaultActor() {
            ToolInvocation invocation = new ToolInvocation("test.tool", Map.of());
            assertEquals("local-user", invocation.getActor());
        }

        @Test
        @DisplayName("Should preserve arguments immutably")
        void shouldPreserveArgumentsImmutably() {
            Map<String, Object> args = new java.util.HashMap<>();
            args.put("key", "original");

            ToolInvocation invocation = new ToolInvocation("test.tool", args);
            args.put("key", "modified");

            assertEquals("original", invocation.getArguments().get("key"));
        }
    }

    @Nested
    @DisplayName("Concurrent Invocation Tests")
    class ConcurrentInvocationTests {

        @Test
        @DisplayName("Should handle concurrent invocations")
        void shouldHandleConcurrentInvocations() throws InterruptedException {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            Thread[] threads = new Thread[10];
            for (int i = 0; i < threads.length; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    try {
                        ToolInvocation invocation = new ToolInvocation("test.echo",
                            Map.of("message", "Thread " + index));
                        requestHandler.handleInvocation(invocation);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join(5000);
            }

            assertEquals(10, successCount.get());
            assertEquals(0, errorCount.get());
        }
    }
}
