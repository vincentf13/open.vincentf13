package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.exception.contractor.BaseRuntimeException;

import java.util.Map;

public class ControllerException extends BaseRuntimeException {
    public ControllerException(ErrorCode error, String errorMessage) {
        super(error, errorMessage);
    }

    public ControllerException(ErrorCode error, String errorMessage, Throwable rootCause) {
        super(error, errorMessage, rootCause);
    }

    public ControllerException(ErrorCode error, String errorMessage, Map<String, Object> meta) {
        super(error, errorMessage, meta);
    }

    public ControllerException(ErrorCode error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(error, errorMessage, meta, rootCause);
    }
}
