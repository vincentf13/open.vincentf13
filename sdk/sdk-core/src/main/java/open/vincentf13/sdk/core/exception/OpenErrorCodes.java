package open.vincentf13.sdk.core.exception;

public enum OpenErrorCodes implements OpenErrorCode {
    /**
     後端統一錯誤碼定義，採 "ERR-<HTTP>-<序號>" 命名以利排查。
     */
    
    INTERNAL_ERROR("Common-500-0001", "Internal server error"),
    REQUEST_VALIDATION_FAILED("Common-400-0001", "Request validation failed"),
    REQUEST_PARAMETER_MISSING("Common-400-0002", "Missing request parameter"),
    REQUEST_PAYLOAD_UNREADABLE("Common-400-0003", "Malformed request payload"),
    HTTP_METHOD_NOT_ALLOWED("Common-405-0001", "HTTP method not allowed"),
    REMOTE_SERVICE_UNAVAILABLE("Common-503-0001", "Remote service temporarily unavailable"),
    REMOTE_SERVICE_ERROR("Common-502-0001", "Remote service call failed"),
    REMOTE_RESPONSE_DECODE_FAILED("Common-502-0002", "Remote response decode failed"),
    REMOTE_REQUEST_ENCODE_FAILED("Common-400-0004", "Remote request encode failed");
    
    private final String code;       // 數字狀態碼字串
    private final String message;    // 英文訊息（對外訊息維持英文）
    
    OpenErrorCodes(String code,
                   String message) {
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
