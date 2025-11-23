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
        String traceId = MDC.get(OpenConstant.HttpHeader.TRACE_ID.value());
        if (traceId != null) {
            meta.put("mdcTraceId", traceId);
        }
        String requestId = MDC.get(OpenConstant.HttpHeader.REQUEST_ID.value());
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
