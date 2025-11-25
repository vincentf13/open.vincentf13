package open.vincentf13.exchange.user.infra;

import open.vincentf13.sdk.core.exception.OpenErrorCode;

public enum UserErrorCode implements OpenErrorCode {
    USER_ALREADY_EXISTS("User-409-1001", "User already exists"),
    USER_NOT_FOUND("User-404-1001", "User not found"),
    USER_AUTH_PREPARATION_FAILED("User-500-1001", "Failed to prepare authentication credential");

    private final String code;
    private final String message;

    UserErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
