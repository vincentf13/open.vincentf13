package open.vincentf13.sdk.auth;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * JWT Filter 相關事件。
 */
public enum JwtAuthEventEnum implements OpenEvent {
    JWT_SESSION_INACTIVE("JwtSessionInactive", "Session inactive, skip authentication");

    private final String event;
    private final String message;

    JwtAuthEventEnum(String event, String message) {
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
