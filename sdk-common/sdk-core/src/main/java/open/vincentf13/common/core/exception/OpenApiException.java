package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.error.OpenError;

import java.util.Map;

public class OpenApiException extends OpenRuntimeException {
    public OpenApiException(OpenError error, String errorMessage) {
        super(error, errorMessage);
    }

    public OpenApiException(OpenError error, String errorMessage, Throwable rootCause) {
        super(error, errorMessage, rootCause);
    }

    public OpenApiException(OpenError error, String errorMessage, Map<String, Object> meta) {
        super(error, errorMessage, meta);
    }

    public OpenApiException(OpenError error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(error, errorMessage, meta, rootCause);
    }
}
