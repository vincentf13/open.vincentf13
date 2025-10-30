package open.vincentf13.exchange.user.domain.error;

import open.vincentf13.common.core.error.OpenError;

public enum UserErrorCode implements OpenError {
    USER_ALREADY_EXISTS("ERR-409-1001", "User already exists"),
    USER_NOT_FOUND("ERR-404-1001", "User not found");

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
