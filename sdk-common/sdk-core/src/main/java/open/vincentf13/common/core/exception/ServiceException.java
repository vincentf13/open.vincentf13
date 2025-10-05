package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.exception.contractor.BaseCheckedException;

public class ServiceException extends BaseCheckedException {
    public ServiceException(ErrorCode error, String errorMessage) {
        super(error, errorMessage);
    }
}
