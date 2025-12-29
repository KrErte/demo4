package com.example.mcp.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.function.Function;

/**
 * Defines an MCP tool with its metadata and execution logic.
 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final JsonNode inputSchema;
    private final Function<Map<String, Object>, Object> executor;

    public ToolDefinition(String name, String description, JsonNode inputSchema,
                          Function<Map<String, Object>, Object> executor) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.executor = executor;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonNode getInputSchema() {
        return inputSchema;
    }

    public Object invoke(Map<String, Object> args) {
        return executor.apply(args);
    }

    public Map<String, Object> toMcpFormat() {
        return Map.of(
            "name", name,
            "description", description,
            "inputSchema", inputSchema
        );
    }
}
