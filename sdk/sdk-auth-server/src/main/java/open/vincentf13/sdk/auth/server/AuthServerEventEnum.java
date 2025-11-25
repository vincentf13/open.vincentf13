package open.vincentf13.sdk.auth.server;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * Auth Server 事件枚舉。
 */
public enum AuthServerEventEnum implements OpenEvent {
    JWT_SESSION_CREATED("JwtSessionCreated", "Session created"),
    REFRESH_MISSING_SESSION("RefreshMissingSession", "Refresh jwtToken does not carry a session id"),
    REFRESH_SESSION_NOT_FOUND("RefreshSessionNotFound", "Unable to locate session for refresh"),
    REFRESH_SUBJECT_MISMATCH("RefreshSubjectMismatch", "Refresh jwtToken subject mismatch"),
    REFRESH_SESSION_INACTIVE("RefreshSessionInactive", "Session already expired or revoked"),
    JWT_SESSION_REFRESHED("JwtSessionRefreshed", "Session refreshed"),
    JWT_SESSION_REVOKED("JwtSessionRevoked", "Session revoked"),
    LOGIN_SUCCESS("LoginSuccess", "User authenticated"),
    LOGIN_FAILURE("LoginFailure", "Authentication failed");

    private final String event;
    private final String message;

    AuthServerEventEnum(String event, String message) {
        this.event = event;
        this.message = message;
    }

    @Override
    public String event() {
        return event;
    }

    @Override
    public String message() {
        return message;
    }
}
