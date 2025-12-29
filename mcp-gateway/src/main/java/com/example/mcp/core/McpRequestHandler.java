package com.example.mcp.core;

import com.example.mcp.model.ToolDefinition;
import com.example.mcp.model.ToolInvocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Handles MCP request lifecycle: parse -> validate -> policy check -> execute -> audit -> response.
 */
@Component
public class McpRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(McpRequestHandler.class);

    private final ToolRegistry toolRegistry;
    private final PolicyEngine policyEngine;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public McpRequestHandler(ToolRegistry toolRegistry, PolicyEngine policyEngine, AuditService auditService) {
        this.toolRegistry = toolRegistry;
        this.policyEngine = policyEngine;
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mcp-executor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Handle a tool invocation request.
     */
    public Object handleInvocation(ToolInvocation invocation) {
        long startTime = System.currentTimeMillis();

        try {
            // 1. Find the tool
            ToolDefinition tool = toolRegistry.get(invocation.getToolName())
                .orElseThrow(() -> McpException.toolNotFound(invocation.getToolName()));

            // 2. Validate input schema
            validateSchema(tool, invocation.getArguments());

            // 3. Policy check
            PolicyEngine.PolicyResult policyResult = policyEngine.evaluate(invocation);
            if (!policyResult.isAllowed()) {
                auditService.logDeny(invocation, policyResult.getReason());
                throw McpException.policyDenied(policyResult.getReason());
            }

            // 4. Execute with timeout
            Object result = executeWithTimeout(tool, invocation.getArguments(), policyResult.getTimeoutMs());

            // 5. Check result size
            String resultJson = objectMapper.writeValueAsString(result);
            long resultSize = resultJson.getBytes(StandardCharsets.UTF_8).length;
            if (resultSize > policyResult.getMaxResultBytes()) {
                long duration = System.currentTimeMillis() - startTime;
                auditService.logError(invocation, "RESULT_TOO_LARGE",
                    "Result size " + resultSize + " exceeds max " + policyResult.getMaxResultBytes(),
                    duration);
                throw McpException.resultTooLarge(invocation.getToolName(), resultSize, policyResult.getMaxResultBytes());
            }

            // 6. Audit success
            long duration = System.currentTimeMillis() - startTime;
            auditService.logAllow(invocation, duration);

            return result;

        } catch (McpException e) {
            long duration = System.currentTimeMillis() - startTime;
            if (!e.isPolicyViolation()) {
                auditService.logError(invocation, e.getErrorCode(), e.getMessage(), duration);
            }
            throw e;
        } catch (JsonProcessingException e) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.logError(invocation, "SERIALIZATION_ERROR", e.getMessage(), duration);
            throw new McpException("Failed to serialize result", "SERIALIZATION_ERROR", e);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.logError(invocation, "EXECUTION_ERROR", e.getMessage(), duration);
            throw new McpException("Tool execution failed: " + e.getMessage(), "EXECUTION_ERROR", e);
        }
    }

    /**
     * Validate the arguments against the tool's input schema.
     */
    private void validateSchema(ToolDefinition tool, Map<String, Object> args) {
        JsonNode schemaNode = tool.getInputSchema();
        if (schemaNode == null || schemaNode.isEmpty()) {
            return; // No schema to validate against
        }

        try {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(schemaNode);
            JsonNode argsNode = objectMapper.valueToTree(args);
            Set<ValidationMessage> errors = schema.validate(argsNode);

            if (!errors.isEmpty()) {
                StringBuilder sb = new StringBuilder("Schema validation failed: ");
                for (ValidationMessage error : errors) {
                    sb.append(error.getMessage()).append("; ");
                }
                throw McpException.validationFailed(sb.toString());
            }
        } catch (McpException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Schema validation error", e);
            throw McpException.validationFailed("Schema validation error: " + e.getMessage());
        }
    }

    /**
     * Execute tool with timeout enforcement.
     */
    private Object executeWithTimeout(ToolDefinition tool, Map<String, Object> args, long timeoutMs) {
        Future<Object> future = executor.submit(() -> tool.invoke(args));

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw McpException.timeout(tool.getName());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof McpException) {
                throw (McpException) cause;
            }
            throw new McpException("Tool execution failed: " + cause.getMessage(), "EXECUTION_ERROR", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpException("Tool execution interrupted", "INTERRUPTED");
        }
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
