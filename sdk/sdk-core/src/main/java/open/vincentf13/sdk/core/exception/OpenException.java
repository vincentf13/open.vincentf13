package open.vincentf13.sdk.core.exception;

import java.util.HashMap;
import java.util.Map;
import open.vincentf13.sdk.core.OpenConstant;
import org.slf4j.MDC;

/*

統一的業務與介面例外，攜帶錯誤碼及 meta。

catch (Throwable t) {
    log.error("系統錯誤", t);
}

logger 會自動輸出：
- 例外類型
- message
- 完整 stack trace
- cause chain
- suppressed 例外

*/

public class OpenException extends RuntimeException {

  private final OpenErrorCode code;
  private final Map<String, Object> meta;

  private OpenException(OpenErrorCode error, Map<String, Object> meta, Throwable rootCause) {
    super(error.message(), rootCause);
    this.code = error;
    this.meta = mergeMeta(meta);
  }

  public static OpenException of(OpenErrorCode error) {
    return new OpenException(error, null, null);
  }

  public static OpenException of(OpenErrorCode error, Throwable rootCause) {
    return new OpenException(error, null, rootCause);
  }

  public static OpenException of(OpenErrorCode error, Map<String, Object> meta) {
    return new OpenException(error, meta, null);
  }

  public static OpenException of(
      OpenErrorCode error, Map<String, Object> meta, Throwable rootCause) {
    return new OpenException(error, meta, rootCause);
  }

  public OpenErrorCode getCode() {
    return code;
  }

  public Map<String, Object> getMeta() {
    return meta;
  }

  private Map<String, Object> initMeta() {
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

  private Map<String, Object> mergeMeta(Map<String, Object> additionalMeta) {
    Map<String, Object> merged = new HashMap<>(initMeta());
    if (additionalMeta != null) {
      merged.putAll(additionalMeta);
    }
    return merged;
  }
}
