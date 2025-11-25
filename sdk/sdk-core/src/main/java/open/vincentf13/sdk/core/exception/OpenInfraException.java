package open.vincentf13.sdk.core.exception;

import open.vincentf13.sdk.core.error.OpenErrorCode;

import java.util.Map;

public class OpenInfraException extends RuntimeException implements OpenException {

    private final String code;
    private final Map<String, Object> meta;

    private OpenInfraException(OpenErrorCode error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(OpenException.resolveMessage(error, errorMessage), rootCause);
        this.code = error.code();
        this.meta = mergeMeta(meta);
    }

    public static OpenInfraException of(OpenErrorCode error, String errorMessage) {
        return new OpenInfraException(error, errorMessage, null, null);
    }

    public static OpenInfraException of(OpenErrorCode error, String errorMessage, Throwable rootCause) {
        return new OpenInfraException(error, errorMessage, null, rootCause);
    }

    public static OpenInfraException of(OpenErrorCode error, String errorMessage, Map<String, Object> meta) {
        return new OpenInfraException(error, errorMessage, meta, null);
    }

    public static OpenInfraException of(OpenErrorCode error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        return new OpenInfraException(error, errorMessage, meta, rootCause);
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
