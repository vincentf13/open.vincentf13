package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.exception.contractor.BaseRuntimeException;

public class ServiceException extends BaseRuntimeException {
    public ServiceException(ErrorCode error, String errorMessage) {
        super(error, errorMessage);
    }
}
