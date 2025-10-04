package open.vincentf13.common.core.exception;

public enum BackendErrorCodes implements ErrorCode {
    /**
     *  HTTP code 使用；
     *  org.springframework.http.HttpStatus
     */

    INTERNAL("50000", "Internal error"),
    VALIDATION_FAILED("50001", "Validation failed");

    private final String code;       // 數字狀態碼字串
    private final String message;    // 英文訊息（對外訊息維持英文）

    BackendErrorCodes(String code, String message) {
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
