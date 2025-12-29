package com.example.mcp.model;

public record Metrics(
    int registeredTools,
    long allowedCount,
    long deniedCount,
    long totalRequests,
    ConfigSummary config
) {
    public record ConfigSummary(
        boolean defaultDeny,
        long timeoutMs,
        long maxResultBytes,
        boolean fsEnabled,
        boolean httpEnabled,
        boolean postgresEnabled
    ) {}
}
