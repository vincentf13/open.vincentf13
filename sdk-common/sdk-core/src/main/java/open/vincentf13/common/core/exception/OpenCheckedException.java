package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.error.OpenError;

import java.util.Map;

public abstract class OpenCheckedException extends Exception implements OpenException {
    private final String code;
    private final Map<String, Object> meta;

    public OpenCheckedException(OpenError error, String errorMessage) {
        super(errorMessage == null ? error.message() : errorMessage);
        this.code = error.code();
        this.meta = initMeta();
    }

    public OpenCheckedException(OpenError error, String errorMessage, Throwable rootCause) {
        super(errorMessage == null ? error.message() : errorMessage, rootCause);
        this.code = error.code();
        this.meta = initMeta();
    }

    public OpenCheckedException(OpenError error, String errorMessage, Map<String, Object> meta) {
        super(errorMessage == null ? error.message() : errorMessage);
        this.code = error.code();
        meta.putAll(initMeta());
        this.meta = meta;
    }

    public OpenCheckedException(OpenError error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(errorMessage == null ? error.message() : errorMessage, rootCause);
        this.code = error.code();
        meta.putAll(initMeta());
        this.meta = meta;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
