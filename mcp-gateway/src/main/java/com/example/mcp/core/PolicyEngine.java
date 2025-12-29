package com.example.mcp.core;

import com.example.mcp.config.PolicyConfig;
import com.example.mcp.config.PolicyConfig.ToolPolicy;
import com.example.mcp.model.ToolInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Policy engine that enforces YAML-based rules for tool execution.
 */
@Component
public class PolicyEngine {

    private static final Logger logger = LoggerFactory.getLogger(PolicyEngine.class);
    private final PolicyConfig config;

    public PolicyEngine(PolicyConfig config) {
        this.config = config;
    }

    /**
     * Check if an invocation is allowed by policy.
     * Returns a PolicyResult with the decision and reason.
     */
    public PolicyResult evaluate(ToolInvocation invocation) {
        String toolName = invocation.getToolName();
        Map<String, Object> args = invocation.getArguments();

        // Check explicit deny list first
        if (config.getDenyTools() != null && config.getDenyTools().contains(toolName)) {
            return PolicyResult.deny("Tool is in deny list: " + toolName);
        }

        // Check per-tool policy
        ToolPolicy toolPolicy = getToolPolicy(toolName);
        if (toolPolicy != null) {
            if (!toolPolicy.isAllow()) {
                return PolicyResult.deny("Tool explicitly denied by policy: " + toolName);
            }
            // Validate allowed arguments if specified
            if (toolPolicy.getAllowedArgs() != null && !toolPolicy.getAllowedArgs().isEmpty()) {
                for (String argKey : args.keySet()) {
                    if (!toolPolicy.getAllowedArgs().contains(argKey)) {
                        return PolicyResult.deny("Argument not allowed: " + argKey);
                    }
                }
            }
            return PolicyResult.allow(toolPolicy.getTimeoutMs(), toolPolicy.getMaxResultBytes());
        }

        // Check explicit allow list
        if (config.getAllowTools() != null && config.getAllowTools().contains(toolName)) {
            return PolicyResult.allow(config.getDefaultTimeoutMs(), config.getDefaultMaxResultBytes());
        }

        // Apply default deny rule
        if (config.isDefaultDeny()) {
            return PolicyResult.deny("Default deny policy in effect - tool not in allow list: " + toolName);
        }

        return PolicyResult.allow(config.getDefaultTimeoutMs(), config.getDefaultMaxResultBytes());
    }

    /**
     * Get the tool-specific policy if defined.
     */
    private ToolPolicy getToolPolicy(String toolName) {
        if (config.getPerTool() == null) {
            return null;
        }
        return config.getPerTool().get(toolName);
    }

    /**
     * Get the timeout for a tool invocation.
     */
    public long getTimeoutMs(String toolName) {
        ToolPolicy policy = getToolPolicy(toolName);
        if (policy != null && policy.getTimeoutMs() > 0) {
            return policy.getTimeoutMs();
        }
        return config.getDefaultTimeoutMs();
    }

    /**
     * Get the max result bytes for a tool invocation.
     */
    public long getMaxResultBytes(String toolName) {
        ToolPolicy policy = getToolPolicy(toolName);
        if (policy != null && policy.getMaxResultBytes() > 0) {
            return policy.getMaxResultBytes();
        }
        return config.getDefaultMaxResultBytes();
    }

    /**
     * Result of a policy evaluation.
     */
    public static final class PolicyResult {
        private final boolean allowed;
        private final String reason;
        private final long timeoutMs;
        private final long maxResultBytes;

        private PolicyResult(boolean allowed, String reason, long timeoutMs, long maxResultBytes) {
            this.allowed = allowed;
            this.reason = reason;
            this.timeoutMs = timeoutMs;
            this.maxResultBytes = maxResultBytes;
        }

        public static PolicyResult allow(long timeoutMs, long maxResultBytes) {
            return new PolicyResult(true, "Policy check passed", timeoutMs, maxResultBytes);
        }

        public static PolicyResult deny(String reason) {
            return new PolicyResult(false, reason, 0, 0);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getReason() {
            return reason;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public long getMaxResultBytes() {
            return maxResultBytes;
        }
    }
}
