package open.vincentf13.common.core.exception;

import open.vincentf13.common.core.error.OpenError;

import java.util.HashMap;
import java.util.Map;

public class OpenServiceException extends Exception implements OpenException {

    private final String code;
    private final Map<String, Object> meta;

    public OpenServiceException(OpenError error, String errorMessage) {
        super(resolveMessage(error, errorMessage));
        this.code = error.code();
        this.meta = initMeta();
    }

    public OpenServiceException(OpenError error, String errorMessage, Throwable rootCause) {
        super(resolveMessage(error, errorMessage), rootCause);
        this.code = error.code();
        this.meta = initMeta();
    }

    public OpenServiceException(OpenError error, String errorMessage, Map<String, Object> meta) {
        super(resolveMessage(error, errorMessage));
        this.code = error.code();
        this.meta = mergeMeta(meta);
    }

    public OpenServiceException(OpenError error, String errorMessage, Map<String, Object> meta, Throwable rootCause) {
        super(resolveMessage(error, errorMessage), rootCause);
        this.code = error.code();
        this.meta = mergeMeta(meta);
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    private static String resolveMessage(OpenError error, String errorMessage) {
        return errorMessage == null ? error.message() : errorMessage;
    }

    private Map<String, Object> mergeMeta(Map<String, Object> additionalMeta) {
        Map<String, Object> merged = new HashMap<>(initMeta());
        if (additionalMeta != null) {
            merged.putAll(additionalMeta);
        }
        return merged;
    }
}
