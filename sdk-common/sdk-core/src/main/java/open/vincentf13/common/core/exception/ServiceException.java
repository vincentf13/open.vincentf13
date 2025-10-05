package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.exception.contractor.BaseCheckedException;

import java.util.Map;

public class ServiceException extends BaseCheckedException {

    public ServiceException(ErrorCode error, String errorMessage) {
        super(error, errorMessage);
    }

    public ServiceException(ErrorCode error, String errorMessage, Throwable rootCause) {
        super(error, errorMessage, rootCause);
    }

    public ServiceException(ErrorCode error, String errorMessage, Map<String, Object> meta) {
        super(error, errorMessage, meta);
    }

    public ServiceException(ErrorCode error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(error, errorMessage, meta, rootCause);
    }
}
