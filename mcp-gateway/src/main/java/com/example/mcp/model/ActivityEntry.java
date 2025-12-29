package com.example.mcp.model;

import java.time.Instant;

public record ActivityEntry(
    Instant ts,
    String tool,
    String decision,
    String reason,
    Long elapsedMs
) {
    public static ActivityEntry allowed(String tool, String reason, long elapsedMs) {
        return new ActivityEntry(Instant.now(), tool, "ALLOWED", reason, elapsedMs);
    }

    public static ActivityEntry denied(String tool, String reason) {
        return new ActivityEntry(Instant.now(), tool, "DENIED", reason, null);
    }

    public static ActivityEntry error(String tool, String reason, long elapsedMs) {
        return new ActivityEntry(Instant.now(), tool, "ERROR", reason, elapsedMs);
    }
}
