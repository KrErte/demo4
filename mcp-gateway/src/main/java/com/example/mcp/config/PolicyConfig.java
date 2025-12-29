package com.example.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the policy engine.
 */
@Configuration
@ConfigurationProperties(prefix = "mcp.policy")
public class PolicyConfig {

    private boolean defaultDeny = true;
    private List<String> allowTools;
    private List<String> denyTools;
    private Map<String, ToolPolicy> perTool;
    private long defaultTimeoutMs = 30000;
    private long defaultMaxResultBytes = 1048576; // 1MB

    public boolean isDefaultDeny() {
        return defaultDeny;
    }

    public void setDefaultDeny(boolean defaultDeny) {
        this.defaultDeny = defaultDeny;
    }

    public List<String> getAllowTools() {
        return allowTools;
    }

    public void setAllowTools(List<String> allowTools) {
        this.allowTools = allowTools;
    }

    public List<String> getDenyTools() {
        return denyTools;
    }

    public void setDenyTools(List<String> denyTools) {
        this.denyTools = denyTools;
    }

    public Map<String, ToolPolicy> getPerTool() {
        return perTool;
    }

    public void setPerTool(Map<String, ToolPolicy> perTool) {
        this.perTool = perTool;
    }

    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public void setDefaultTimeoutMs(long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public long getDefaultMaxResultBytes() {
        return defaultMaxResultBytes;
    }

    public void setDefaultMaxResultBytes(long defaultMaxResultBytes) {
        this.defaultMaxResultBytes = defaultMaxResultBytes;
    }

    /**
     * Per-tool policy configuration.
     */
    public static class ToolPolicy {
        private boolean allow = true;
        private long timeoutMs = 30000;
        private long maxResultBytes = 1048576;
        private List<String> allowedArgs;

        public boolean isAllow() {
            return allow;
        }

        public void setAllow(boolean allow) {
            this.allow = allow;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public long getMaxResultBytes() {
            return maxResultBytes;
        }

        public void setMaxResultBytes(long maxResultBytes) {
            this.maxResultBytes = maxResultBytes;
        }

        public List<String> getAllowedArgs() {
            return allowedArgs;
        }

        public void setAllowedArgs(List<String> allowedArgs) {
            this.allowedArgs = allowedArgs;
        }
    }
}
