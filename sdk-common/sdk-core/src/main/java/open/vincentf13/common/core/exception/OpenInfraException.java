package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.error.OpenError;

import java.util.Map;

public class OpenInfraException extends OpenRuntimeException {
    public OpenInfraException(OpenError error, String errorMessage) {
        super(error, errorMessage);
    }

    public OpenInfraException(OpenError error, String errorMessage, Throwable rootCause) {
        super(error, errorMessage, rootCause);
    }

    public OpenInfraException(OpenError error, String errorMessage, Map<String, Object> meta) {
        super(error, errorMessage, meta);
    }

    public OpenInfraException(OpenError error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(error, errorMessage, meta, rootCause);
    }
}
