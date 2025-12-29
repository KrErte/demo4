package com.example.mcp.core;

import com.example.mcp.model.AuditEvent;
import com.example.mcp.model.ToolInvocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Service for audit logging of tool invocations.
 * Logs structured JSON to stdout.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private final ObjectMapper objectMapper;

    public AuditService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Log an allowed invocation.
     */
    public void logAllow(ToolInvocation invocation, long durationMs) {
        AuditEvent event = AuditEvent.builder()
            .timestamp(invocation.getTimestamp())
            .toolName(invocation.getToolName())
            .actor(invocation.getActor())
            .argsHash(hashArguments(invocation.getArguments()))
            .decision(AuditEvent.Decision.ALLOW)
            .reason("Policy check passed")
            .durationMs(durationMs)
            .invocationId(invocation.getId())
            .build();
        writeEvent(event);
    }

    /**
     * Log a denied invocation.
     */
    public void logDeny(ToolInvocation invocation, String reason) {
        AuditEvent event = AuditEvent.builder()
            .timestamp(invocation.getTimestamp())
            .toolName(invocation.getToolName())
            .actor(invocation.getActor())
            .argsHash(hashArguments(invocation.getArguments()))
            .decision(AuditEvent.Decision.DENY)
            .reason(reason)
            .invocationId(invocation.getId())
            .build();
        writeEvent(event);
    }

    /**
     * Log an error during invocation.
     */
    public void logError(ToolInvocation invocation, String errorCode, String reason, long durationMs) {
        AuditEvent event = AuditEvent.builder()
            .timestamp(invocation.getTimestamp())
            .toolName(invocation.getToolName())
            .actor(invocation.getActor())
            .argsHash(hashArguments(invocation.getArguments()))
            .decision(AuditEvent.Decision.ALLOW)
            .reason(reason)
            .durationMs(durationMs)
            .errorCode(errorCode)
            .invocationId(invocation.getId())
            .build();
        writeEvent(event);
    }

    private void writeEvent(AuditEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            System.out.println("[AUDIT] " + json);
            logger.info("Audit: {}", json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize audit event", e);
        }
    }

    /**
     * Compute SHA-256 hash of arguments for audit logging.
     */
    public String hashArguments(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "empty";
        }
        try {
            String json = objectMapper.writeValueAsString(args);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            logger.warn("Failed to hash arguments", e);
            return "hash-error";
        }
    }
}
