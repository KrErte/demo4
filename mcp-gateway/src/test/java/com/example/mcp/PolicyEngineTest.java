package com.example.mcp;

import com.example.mcp.config.PolicyConfig;
import com.example.mcp.core.PolicyEngine;
import com.example.mcp.model.ToolInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the PolicyEngine.
 */
class PolicyEngineTest {

    private PolicyConfig config;
    private PolicyEngine policyEngine;

    @BeforeEach
    void setUp() {
        config = new PolicyConfig();
        config.setDefaultDeny(true);
        config.setDefaultTimeoutMs(30000);
        config.setDefaultMaxResultBytes(1048576);
    }

    @Nested
    @DisplayName("Default Deny Policy Tests")
    class DefaultDenyTests {

        @Test
        @DisplayName("Should deny tool not in allow list when defaultDeny is true")
        void shouldDenyToolNotInAllowList() {
            config.setAllowTools(List.of("fs.readFile"));
            policyEngine = new PolicyEngine(config);

            ToolInvocation invocation = new ToolInvocation("web.fetch", Map.of("url", "https://example.com"));
            PolicyEngine.PolicyResult result = policyEngine.evaluate(invocation);

            assertFalse(result.isAllowed());
            assertTrue(result.getReason().contains("not in allow list"));
        }

        @Test
        @DisplayName("Should allow tool in allow list when defaultDeny is true")
        void shouldAllowToolInAllowList() {
            config.setAllowTools(List.of("fs.readFile", "web.fetch"));
            policyEngine = new PolicyEngine(config);

            ToolInvocation invocation = new ToolInvocation("fs.readFile", Map.of("path", "/tmp/test.txt"));
            PolicyEngine.PolicyResult result = policyEngine.evaluate(invocation);

            assertTrue(result.isAllowed());
        }

        @Test
        @DisplayName("Should allow tool when defaultDeny is false and not in deny list")
        void shouldAllowWhenDefaultDenyFalse() {
            config.setDefaultDeny(false);
            policyEngine = new PolicyEngine(config);

            ToolInvocation invocation = new ToolInvocation("some.random.tool", Map.of());
            PolicyEngine.PolicyResult result = policyEngine.evaluate(invocation);

            assertTrue(result.isAllowed());
        }
    }

    @Nested
    @DisplayName("Deny List Tests")
    class DenyListTests {

        @Test
        @DisplayName("Should deny tool explicitly in deny list")
        void shouldDenyToolInDenyList() {
            config.setDefaultDeny(false);
            config.setDenyTools(List.of("dangerous.tool"));
            policyEngine = new PolicyEngine(config);

            ToolInvocation invocation = new ToolInvocation("dangerous.tool", Map.of());
            PolicyEngine.PolicyResult result = policyEngine.evaluate(invocation);

            assertFalse(result.isAllowed());
            assertTrue(result.getReason().contains("deny list"));
        }

        @Test
        @DisplayName("Deny list should take precedence over allow list")
        void denyListTakesPrecedence() {
            config.setAllowTools(List.of("some.tool"));
            config.setDenyTools(List.of("some.tool"));
            policyEngine = new PolicyEngine(config);

            ToolInvocation invocation = new ToolInvocation("some.tool", Map.of());
            PolicyEngine.PolicyResult result = policyEngine.evaluate(invocation);

            assertFalse(result.isAllowed());
        }
    }

    @Nested
    @DisplayName("Per-Tool Policy Tests")
    class PerToolPolicyTests {

        @Test
        @DisplayName("Should respect per-tool allow setting")
        void shouldRespectPerToolAllow() {
            PolicyConfig.ToolPolicy toolPolicy = new PolicyConfig.ToolPolicy();
            toolPolicy.setAllow(true);
            toolPolicy.setTimeoutMs(5000);
            toolPolicy.setMaxResultBytes(100000);

            config.setPerTool(Map.of("special.tool", toolPolicy));
            policyEngine = new PolicyEngine(config);

            ToolInvocation invocation = new ToolInvocation("special.tool", Map.of());
            PolicyEngine.PolicyResult result = policyEngine.evaluate(invocation);

            assertTrue(result.isAllowed());
            assertEquals(5000, result.getTimeoutMs());
            assertEquals(100000, result.getMaxResultBytes());
        }

        @Test
        @DisplayName("Should deny per-tool when allow is false")
        void shouldDenyPerToolWhenAllowFalse() {
            PolicyConfig.ToolPolicy toolPolicy = new PolicyConfig.ToolPolicy();
            toolPolicy.setAllow(false);

            config.setPerTool(Map.of("blocked.tool", toolPolicy));
            policyEngine = new PolicyEngine(config);

            ToolInvocation invocation = new ToolInvocation("blocked.tool", Map.of());
            PolicyEngine.PolicyResult result = policyEngine.evaluate(invocation);

            assertFalse(result.isAllowed());
            assertTrue(result.getReason().contains("explicitly denied"));
        }

        @Test
        @DisplayName("Should validate allowed arguments")
        void shouldValidateAllowedArguments() {
            PolicyConfig.ToolPolicy toolPolicy = new PolicyConfig.ToolPolicy();
            toolPolicy.setAllow(true);
            toolPolicy.setAllowedArgs(List.of("path"));

            config.setPerTool(Map.of("fs.readFile", toolPolicy));
            policyEngine = new PolicyEngine(config);

            // Valid arguments
            ToolInvocation validInvocation = new ToolInvocation("fs.readFile", Map.of("path", "/tmp/test.txt"));
            PolicyEngine.PolicyResult validResult = policyEngine.evaluate(validInvocation);
            assertTrue(validResult.isAllowed());

            // Invalid arguments
            ToolInvocation invalidInvocation = new ToolInvocation("fs.readFile",
                Map.of("path", "/tmp/test.txt", "forbidden", "value"));
            PolicyEngine.PolicyResult invalidResult = policyEngine.evaluate(invalidInvocation);
            assertFalse(invalidResult.isAllowed());
            assertTrue(invalidResult.getReason().contains("not allowed"));
        }
    }

    @Nested
    @DisplayName("Timeout and Size Limits Tests")
    class LimitsTests {

        @Test
        @DisplayName("Should return default timeout when no per-tool policy")
        void shouldReturnDefaultTimeout() {
            config.setAllowTools(List.of("some.tool"));
            policyEngine = new PolicyEngine(config);

            long timeout = policyEngine.getTimeoutMs("some.tool");
            assertEquals(30000, timeout);
        }

        @Test
        @DisplayName("Should return per-tool timeout when configured")
        void shouldReturnPerToolTimeout() {
            PolicyConfig.ToolPolicy toolPolicy = new PolicyConfig.ToolPolicy();
            toolPolicy.setAllow(true);
            toolPolicy.setTimeoutMs(10000);

            config.setPerTool(Map.of("fast.tool", toolPolicy));
            policyEngine = new PolicyEngine(config);

            long timeout = policyEngine.getTimeoutMs("fast.tool");
            assertEquals(10000, timeout);
        }

        @Test
        @DisplayName("Should return default max result bytes when no per-tool policy")
        void shouldReturnDefaultMaxResultBytes() {
            config.setAllowTools(List.of("some.tool"));
            policyEngine = new PolicyEngine(config);

            long maxBytes = policyEngine.getMaxResultBytes("some.tool");
            assertEquals(1048576, maxBytes);
        }

        @Test
        @DisplayName("Should return per-tool max result bytes when configured")
        void shouldReturnPerToolMaxResultBytes() {
            PolicyConfig.ToolPolicy toolPolicy = new PolicyConfig.ToolPolicy();
            toolPolicy.setAllow(true);
            toolPolicy.setMaxResultBytes(500000);

            config.setPerTool(Map.of("limited.tool", toolPolicy));
            policyEngine = new PolicyEngine(config);

            long maxBytes = policyEngine.getMaxResultBytes("limited.tool");
            assertEquals(500000, maxBytes);
        }
    }
}
