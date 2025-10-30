package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.error.OpenError;

import java.util.Map;

public class OpenServiceException extends RuntimeException implements OpenException {

    private final String code;
    private final Map<String, Object> meta;

    private OpenServiceException(OpenError error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(OpenException.resolveMessage(error, errorMessage), rootCause);
        this.code = error.code();
        this.meta = mergeMeta(meta);
    }

    public static OpenServiceException of(OpenError error, String errorMessage) {
        return new OpenServiceException(error, errorMessage, null, null);
    }

    public static OpenServiceException of(OpenError error, String errorMessage, Throwable rootCause) {
        return new OpenServiceException(error, errorMessage, null, rootCause);
    }

    public static OpenServiceException of(OpenError error, String errorMessage, Map<String, Object> meta) {
        return new OpenServiceException(error, errorMessage, meta, null);
    }

    public static OpenServiceException of(OpenError error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        return new OpenServiceException(error, errorMessage, meta, rootCause);
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
