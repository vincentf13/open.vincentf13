package open.vincentf13.sdk.auth.jwt;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
  JWT 相關事件。
 */
public enum JwtEvent implements OpenEvent {
    JWT_ACCESS_ISSUED("JwtAccessIssued", "Access jwtToken issued"),
    JWT_REFRESH_ISSUED("JwtRefreshIssued", "Refresh jwtToken issued"),
    JWT_INVALID_TYPE("JwtInvalidType", "Token type mismatch"),
    JWT_INVALID("JwtInvalid", "JWT validation failed"),
    JWT_UNKNOWN_TYPE("JwtUnknownType", "Unknown jwtToken type"),
    REDIS_SESSION_REVOKED("RedisSessionRevoked", "Session revoked in redis"),
    IN_MEMORY_SESSION_REVOKED("InMemorySessionRevoked", "Session revoked in memory"),
    JWT_SESSION_INACTIVE("JwtSessionInactive", "Session inactive, skip authentication");

    private final String event;
    private final String message;

    JwtEvent(String event, String message) {
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
