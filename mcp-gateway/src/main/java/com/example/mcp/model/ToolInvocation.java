package com.example.mcp.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an invocation request for an MCP tool.
 */
public final class ToolInvocation {

    private final String id;
    private final String toolName;
    private final Map<String, Object> arguments;
    private final Instant timestamp;
    private final String actor;

    public ToolInvocation(String toolName, Map<String, Object> arguments) {
        this.id = UUID.randomUUID().toString();
        this.toolName = toolName;
        this.arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        this.timestamp = Instant.now();
        this.actor = "local-user";
    }

    public ToolInvocation(String toolName, Map<String, Object> arguments, String actor) {
        this.id = UUID.randomUUID().toString();
        this.toolName = toolName;
        this.arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        this.timestamp = Instant.now();
        this.actor = actor;
    }

    public String getId() {
        return id;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getActor() {
        return actor;
    }
}
