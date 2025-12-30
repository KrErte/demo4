package com.example.mcp.model;

/**
 * Login response with token and user info.
 */
public record LoginResponse(
    boolean ok,
    String token,
    User.UserInfo user,
    String error
) {
    public static LoginResponse success(String token, User.UserInfo user) {
        return new LoginResponse(true, token, user, null);
    }

    public static LoginResponse error(String error) {
        return new LoginResponse(false, null, null, error);
    }
}
