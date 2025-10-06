package open.vincentf13.common.core.exception;

public enum BackendErrorCodes implements ErrorCode {
    /**
     * 後端統一錯誤碼定義，採 "ERR-<HTTP>-<序號>" 命名以利排查。
     */

    INTERNAL_ERROR("ERR-500-0001", "Internal server error"),
    REQUEST_VALIDATION_FAILED("ERR-400-0001", "Request validation failed"),
    REQUEST_PARAMETER_MISSING("ERR-400-0002", "Missing request parameter"),
    REQUEST_PAYLOAD_UNREADABLE("ERR-400-0003", "Malformed request payload"),
    HTTP_METHOD_NOT_ALLOWED("ERR-405-0001", "HTTP method not allowed");

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
