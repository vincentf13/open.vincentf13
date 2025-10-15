package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.error.OpenError;

import java.util.Map;

public class OpenServiceException extends Exception implements OpenException {

    private final String code;
    private final Map<String, Object> meta;

    public OpenServiceException(OpenError error, String errorMessage) {
        super(OpenException.resolveMessage(error, errorMessage));
        this.code = error.code();
        this.meta = initMeta();
    }

    public OpenServiceException(OpenError error, String errorMessage, Throwable rootCause) {
        super(OpenException.resolveMessage(error, errorMessage), rootCause);
        this.code = error.code();
        this.meta = initMeta();
    }

    public OpenServiceException(OpenError error, String errorMessage, Map<String, Object> meta) {
        super(OpenException.resolveMessage(error, errorMessage));
        this.code = error.code();
        this.meta = mergeMeta(meta);
    }

    public OpenServiceException(OpenError error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(OpenException.resolveMessage(error, errorMessage), rootCause);
        this.code = error.code();
        this.meta = mergeMeta(meta);
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
