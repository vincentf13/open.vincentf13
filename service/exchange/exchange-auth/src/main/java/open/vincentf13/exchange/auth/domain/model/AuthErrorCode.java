package open.vincentf13.exchange.auth.domain.model;

import open.vincentf13.sdk.core.error.OpenError;

public enum AuthErrorCode implements OpenError {
    AUTH_CREDENTIAL_ALREADY_EXISTS("Auth-409-1001", "Credential already exists"),
    AUTH_CREDENTIAL_NOT_FOUND("Auth-404-1001", "Credential not found");

    private final String code;
    private final String message;

    AuthErrorCode(String code, String message) {
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
