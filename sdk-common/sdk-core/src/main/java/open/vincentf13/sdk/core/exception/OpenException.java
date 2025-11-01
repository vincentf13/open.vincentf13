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
        return Map.of(
                "mdcTraceId", MDC.get(OpenConstant.TRACE_ID_KEY),
                "mdcRequestId", MDC.get(OpenConstant.REQUEST_ID_KEY)
        );
    }

    default Map<String, Object> mergeMeta(Map<String, Object> additionalMeta) {
        Map<String, Object> merged = new HashMap<>(initMeta());
        if (additionalMeta != null) {
            merged.putAll(additionalMeta);
        }
        return merged;
    }
}
