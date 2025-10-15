package open.vincentf13.common.core.exception;

import org.slf4j.MDC;

import java.util.Map;

public interface OpenException {
       // 可被實作類別覆寫
    default Map<String, Object> initMeta() {
        return Map.of(
                "mdcTraceId", MDC.get("traceId"),
                "mdcRequestId", MDC.get("requestId")
        );
    }

}
