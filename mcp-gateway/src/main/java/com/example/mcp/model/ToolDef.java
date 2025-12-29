package com.example.mcp.model;

import java.util.Map;

public record ToolDef(
    String name,
    String description,
    Map<String, Object> inputSchema
) {}
