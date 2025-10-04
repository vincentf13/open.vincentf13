package open.vincentf13.common.core.exception.contractor;

import open.vincentf13.common.core.exception.ErrorCode;

import java.util.Map;

public abstract class BaseCheckedException extends Exception {
    private final String code;
    private final Map<String, Object> meta;

    public BaseCheckedException(ErrorCode error, String errorMessage) {
        super(errorMessage == null ? error.message() : errorMessage);
        this.code = error.code();
        this.meta = null;
    }

    public BaseCheckedException(ErrorCode error, String errorMessage, Throwable rootCause) {
        super(errorMessage == null ? error.message() : errorMessage
                , rootCause);
        this.code = error.code();
        this.meta = null;
    }

    public BaseCheckedException(ErrorCode error, String errorMessage, Map<String, Object> meta) {
        super(errorMessage == null ? error.message() : errorMessage);
        this.code = error.code();
        this.meta = meta;
    }

    public BaseCheckedException(ErrorCode error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(errorMessage == null ? error.message() : errorMessage
                , rootCause);
        this.code = error.code();
        this.meta = meta;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
