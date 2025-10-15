package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.error.OpenError;

import java.util.Map;

public class OpenServiceException extends OpenCheckedException {

    public OpenServiceException(OpenError error, String errorMessage) {
        super(error, errorMessage);
    }

    public OpenServiceException(OpenError error, String errorMessage, Throwable rootCause) {
        super(error, errorMessage, rootCause);
    }

    public OpenServiceException(OpenError error, String errorMessage, Map<String, Object> meta) {
        super(error, errorMessage, meta);
    }

    public OpenServiceException(OpenError error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(error, errorMessage, meta, rootCause);
    }
}
