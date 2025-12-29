package com.example.mcp.model;

import java.util.Map;

public record CallRequest(
    String tool,
    Map<String, Object> arguments
) {}
