package com.example.mcp.core;

import com.example.mcp.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for MCP tools. Connectors register their tools here.
 */
@Component
public class ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);
    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /**
     * Register a tool with the registry.
     */
    public void register(ToolDefinition tool) {
        if (tool == null || tool.getName() == null) {
            throw new IllegalArgumentException("Tool and tool name must not be null");
        }
        if (tools.containsKey(tool.getName())) {
            logger.warn("Overwriting existing tool: {}", tool.getName());
        }
        tools.put(tool.getName(), tool);
        logger.info("Registered tool: {}", tool.getName());
    }

    /**
     * Get a tool by name.
     */
    public Optional<ToolDefinition> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * Check if a tool exists.
     */
    public boolean exists(String name) {
        return tools.containsKey(name);
    }

    /**
     * Get all registered tools.
     */
    public Collection<ToolDefinition> getAll() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * Get all tool names.
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /**
     * Remove a tool from the registry.
     */
    public void unregister(String name) {
        if (tools.remove(name) != null) {
            logger.info("Unregistered tool: {}", name);
        }
    }

    /**
     * Clear all tools from the registry.
     */
    public void clear() {
        tools.clear();
        logger.info("Cleared all tools from registry");
    }

    /**
     * Get the number of registered tools.
     */
    public int size() {
        return tools.size();
    }
}
