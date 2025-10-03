package open.vincentf13.sdk.core.code;

public enum HttpErrorCodes implements ErrorCode {
    OK("200", "OK"),
    BAD_REQUEST("400", "Bad request"),
    UNAUTHORIZED("401", "Unauthorized"),
    FORBIDDEN("403", "Forbidden"),
    NOT_FOUND("404", "Not found"),
    CONFLICT("409", "Conflict"),
    INTERNAL_ERROR("500", "Internal error");

    private final String code;
    private final String message;
    HttpErrorCodes(String code, String message) { this.code = code; this.message = message; }
    public String code() { return code; }
    public String message() { return message; }
}
