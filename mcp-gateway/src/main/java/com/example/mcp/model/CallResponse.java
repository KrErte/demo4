package com.example.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallResponse(
    boolean ok,
    Object result,
    String error,
    String code,
    Long elapsedMs,
    Boolean truncated
) {
    public static CallResponse success(Object result, long elapsedMs, boolean truncated) {
        return new CallResponse(true, result, null, null, elapsedMs, truncated ? true : null);
    }

    public static CallResponse error(String error, String code) {
        return new CallResponse(false, null, error, code, null, null);
    }
}
