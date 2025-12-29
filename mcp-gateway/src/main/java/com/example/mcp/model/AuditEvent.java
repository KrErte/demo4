package com.example.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Audit event for tool invocations, logged as structured JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AuditEvent {

    public enum Decision {
        ALLOW, DENY
    }

    private final Instant timestamp;
    private final String toolName;
    private final String actor;
    private final String argsHash;
    private final Decision decision;
    private final String reason;
    private final Long durationMs;
    private final String errorCode;
    private final String invocationId;

    private AuditEvent(Builder builder) {
        this.timestamp = builder.timestamp;
        this.toolName = builder.toolName;
        this.actor = builder.actor;
        this.argsHash = builder.argsHash;
        this.decision = builder.decision;
        this.reason = builder.reason;
        this.durationMs = builder.durationMs;
        this.errorCode = builder.errorCode;
        this.invocationId = builder.invocationId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getToolName() {
        return toolName;
    }

    public String getActor() {
        return actor;
    }

    public String getArgsHash() {
        return argsHash;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getInvocationId() {
        return invocationId;
    }

    public static final class Builder {
        private Instant timestamp = Instant.now();
        private String toolName;
        private String actor;
        private String argsHash;
        private Decision decision;
        private String reason;
        private Long durationMs;
        private String errorCode;
        private String invocationId;

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder argsHash(String argsHash) {
            this.argsHash = argsHash;
            return this;
        }

        public Builder decision(Decision decision) {
            this.decision = decision;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder invocationId(String invocationId) {
            this.invocationId = invocationId;
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}
