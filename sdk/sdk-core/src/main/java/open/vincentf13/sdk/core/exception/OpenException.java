package open.vincentf13.sdk.core.exception;

import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.core.error.OpenError;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public interface OpenException {

    static String resolveMessage(OpenError error, String fallbackMessage) {
        return fallbackMessage == null ? error.message() : fallbackMessage;
    }

    default Map<String, Object> initMeta() {
        Map<String, Object> meta = new HashMap<>();
        String traceId = MDC.get(OpenConstant.TRACE_ID_KEY);
        if (traceId != null) {
            meta.put("mdcTraceId", traceId);
        }
        String requestId = MDC.get(OpenConstant.REQUEST_ID_KEY);
        if (requestId != null) {
            meta.put("mdcRequestId", requestId);
        }
        return meta;
    }

    default Map<String, Object> mergeMeta(Map<String, Object> additionalMeta) {
        Map<String, Object> merged = new HashMap<>(initMeta());
        if (additionalMeta != null) {
            merged.putAll(additionalMeta);
        }
        return merged;
    }
}
