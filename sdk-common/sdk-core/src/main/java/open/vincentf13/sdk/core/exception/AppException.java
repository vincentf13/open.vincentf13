package open.vincentf13.sdk.core.exception;

import open.vincentf13.sdk.core.code.ErrorCode;

import java.util.Map;

public class AppException extends RuntimeException {
    private final String code;
    private final Map<String, Object> meta;

    public AppException(ErrorCode error, String detail) {
        super(detail == null ? error.message() : detail);
        this.code = error.code();
        this.meta = null;
    }

    public AppException(ErrorCode error, String detail, Map<String, Object> meta) {
        super(detail == null ? error.message() : detail);
        this.code = error.code();
        this.meta = meta;
    }

    public String getCode() { return code; }
    public Map<String, Object> getMeta() { return meta; }
}
