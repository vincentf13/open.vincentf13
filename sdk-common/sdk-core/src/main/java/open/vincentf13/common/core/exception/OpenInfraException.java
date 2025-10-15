package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.error.OpenError;

import java.util.Map;

public class OpenInfraException extends RuntimeException implements OpenException {

    private final String code;
    private final Map<String, Object> meta;

    public OpenInfraException(OpenError error, String errorMessage) {
        super(OpenException.resolveMessage(error, errorMessage));
        this.code = error.code();
        this.meta = initMeta();
    }

    public OpenInfraException(OpenError error, String errorMessage, Throwable rootCause) {
        super(OpenException.resolveMessage(error, errorMessage), rootCause);
        this.code = error.code();
        this.meta = initMeta();
    }

    public OpenInfraException(OpenError error, String errorMessage, Map<String, Object> meta) {
        super(OpenException.resolveMessage(error, errorMessage));
        this.code = error.code();
        this.meta = mergeMeta(meta);
    }

    public OpenInfraException(OpenError error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
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
