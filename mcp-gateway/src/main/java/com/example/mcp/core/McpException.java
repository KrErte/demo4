package com.example.mcp.core;

/**
 * Exception thrown during MCP operations.
 */
public class McpException extends RuntimeException {

    private final String errorCode;
    private final boolean policyViolation;

    public McpException(String message) {
        super(message);
        this.errorCode = "MCP_ERROR";
        this.policyViolation = false;
    }

    public McpException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.policyViolation = false;
    }

    public McpException(String message, String errorCode, boolean policyViolation) {
        super(message);
        this.errorCode = errorCode;
        this.policyViolation = policyViolation;
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "MCP_ERROR";
        this.policyViolation = false;
    }

    public McpException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.policyViolation = false;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isPolicyViolation() {
        return policyViolation;
    }

    public static McpException policyDenied(String reason) {
        return new McpException(reason, "POLICY_DENIED", true);
    }

    public static McpException toolNotFound(String toolName) {
        return new McpException("Tool not found: " + toolName, "TOOL_NOT_FOUND");
    }

    public static McpException validationFailed(String reason) {
        return new McpException(reason, "VALIDATION_FAILED");
    }

    public static McpException timeout(String toolName) {
        return new McpException("Tool execution timed out: " + toolName, "TIMEOUT");
    }

    public static McpException resultTooLarge(String toolName, long size, long maxSize) {
        return new McpException(
            String.format("Result from %s exceeds max size: %d > %d bytes", toolName, size, maxSize),
            "RESULT_TOO_LARGE"
        );
    }
}
