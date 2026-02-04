package open.vincentf13.sdk.spring.cloud.openfeign.exception;

import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;
import feign.codec.EncodeException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.core.exception.OpenErrorCodes;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.cloud.openfeign.FeignEvent;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全域 Feign 例外處理器，統一封裝遠端呼叫異常的 API 回應格式。 */
@RestControllerAdvice
@ConditionalOnClass(FeignException.class)
public class OpenFeignExceptionAdvice implements MessageSourceAware {

  private MessageSourceAccessor messageAccessor;

  /** 注入 MessageSource 用於國際化訊息解析 */
  @Override
  public void setMessageSource(MessageSource messageSource) {
    this.messageAccessor = new MessageSourceAccessor(messageSource);
  }

  /**
   * 處理可重試的 Feign 例外 - 因網路逾時或暫時性故障等原因導致的、經過內部重試後依然失敗的 Feign 例外 - 記錄目標 URL 到日誌 - 回傳 503
   * SERVICE_UNAVAILABLE - metadata 中包含遠端請求資訊
   */
  @ExceptionHandler(RetryableException.class)
  public ResponseEntity<OpenApiResponse<Object>> handleRetryable(
      RetryableException ex, HttpServletRequest request) {
    OpenLog.warn(
        FeignEvent.FEIGN_RETRYABLE_EXCEPTION,
        ex,
        "target",
        ex.request() != null ? ex.request().url() : "unknown");
    Map<String, Object> additionalMeta = new LinkedHashMap<>();
    enrichWithFeignRequest(additionalMeta, ex);
    return buildErrorResponse(
        request,
        HttpStatus.SERVICE_UNAVAILABLE,
        OpenErrorCodes.REMOTE_SERVICE_UNAVAILABLE,
        "error.remote.retryable",
        additionalMeta);
  }

  /**
   * 處理一般 Feign 例外（HTTP 錯誤等） - 從例外中解析遠端服務回傳的 HTTP 狀態碼 - 記錄狀態碼和目標 URL 到日誌 - 回傳對應的 HTTP 狀態碼（若無法解析則回傳
   * 502） - metadata 中包含遠端狀態碼、請求資訊和錯誤原因
   */
  @ExceptionHandler(FeignException.class)
  public ResponseEntity<OpenApiResponse<Object>> handleFeign(
      FeignException ex, HttpServletRequest request) {
    HttpStatus status = HttpStatus.resolve(ex.status());
    if (status == null) {
      status = HttpStatus.BAD_GATEWAY;
    }
    OpenLog.warn(
        FeignEvent.FEIGN_EXCEPTION,
        ex,
        "status",
        ex.status(),
        "target",
        ex.request() != null ? ex.request().url() : "unknown");
    Map<String, Object> additionalMeta = new LinkedHashMap<>();
    enrichWithFeignRequest(additionalMeta, ex);
    additionalMeta.put("feignStatus", ex.status());
    if (StringUtils.hasText(ex.getMessage())) {
      additionalMeta.put("reason", ex.getMessage());
    }
    return buildErrorResponse(
        request,
        status,
        OpenErrorCodes.REMOTE_SERVICE_ERROR,
        "error.remote.failure",
        additionalMeta);
  }

  /*
   處理 Feign 回應解碼失敗
   - 當無法將遠端服務回應解碼為預期的物件時觸發
   - 回傳 502 BAD_GATEWAY
  */
  @ExceptionHandler(DecodeException.class)
  public ResponseEntity<OpenApiResponse<Object>> handleDecode(
      DecodeException ex, HttpServletRequest request) {
    OpenLog.warn(FeignEvent.FEIGN_DECODE_EXCEPTION, ex);
    return buildErrorResponse(
        request,
        HttpStatus.BAD_GATEWAY,
        OpenErrorCodes.REMOTE_RESPONSE_DECODE_FAILED,
        "error.remote.decode",
        Map.of());
  }

  /*
   處理 Feign 請求編碼失敗
   - 當無法將請求物件編碼為遠端服務所需的格式時觸發
   - 回傳 400 BAD_REQUEST
  */
  @ExceptionHandler(EncodeException.class)
  public ResponseEntity<OpenApiResponse<Object>> handleEncode(
      EncodeException ex, HttpServletRequest request) {
    OpenLog.warn(FeignEvent.FEIGN_ENCODE_EXCEPTION, ex);
    return buildErrorResponse(
        request,
        HttpStatus.BAD_REQUEST,
        OpenErrorCodes.REMOTE_REQUEST_ENCODE_FAILED,
        "error.remote.encode",
        Map.of());
  }

  /*
   建立統一格式的錯誤回應
   - 組裝基礎 metadata（status, timestamp, path, traceId 等）
   - 合併額外的 metadata
   - 解析國際化錯誤訊息
   - 封裝成 OpenApiResponse 並包裝在 ResponseEntity 中
  */
  private ResponseEntity<OpenApiResponse<Object>> buildErrorResponse(
      HttpServletRequest request,
      HttpStatus status,
      open.vincentf13.sdk.core.exception.OpenErrorCode errorCode,
      String messageCode,
      Map<String, Object> additionalMeta) {
    Map<String, Object> meta = baseMeta(request, status);
    if (additionalMeta != null && !additionalMeta.isEmpty()) {
      meta.putAll(additionalMeta);
    }
    OpenApiResponse<Object> body =
        OpenApiResponse.failure(
            errorCode.code(), resolveMessage(messageCode, errorCode.message()), meta);
    return ResponseEntity.status(status).body(body);
  }

  /*
   組裝統一的 metadata 段落
   - status: HTTP 狀態碼
   - timestamp: 當前時間戳（ISO-8601 格式）
   - path: 請求路徑
   - method: HTTP 方法
   - traceId 和 requestId: 從 RequestCorrelationFilter 設置的 attribute 中取得
  */
  private Map<String, Object> baseMeta(@Nullable HttpServletRequest request, HttpStatus status) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("status", status.value());
    meta.put("timestamp", Instant.now().toString());
    if (request != null) {
      meta.put("path", request.getRequestURI());
      meta.put("method", request.getMethod());
      meta.put(
          OpenConstant.HttpHeader.TRACE_ID.value(),
          request.getAttribute(OpenConstant.HttpHeader.TRACE_ID.value()));
    }
    return meta;
  }

  /*
   從 Feign 例外中提取遠端請求資訊到 metadata
   - remoteUrl: 遠端服務的完整 URL
   - remoteMethod: HTTP 方法（GET, POST 等）
  */
  private void enrichWithFeignRequest(Map<String, Object> meta, FeignException ex) {
    if (ex.request() == null) {
      return;
    }
    meta.put("remoteUrl", ex.request().url());
    if (ex.request().httpMethod() != null) {
      meta.put("remoteMethod", ex.request().httpMethod().name());
    }
  }

  /*
   解析錯誤代碼的國際化訊息
   - 根據訊息代碼和當前 Locale 從 MessageSource 解析
   - 若無 MessageSource 或找不到訊息，則使用預設訊息
  */
  private String resolveMessage(String code, String defaultMessage) {
    if (messageAccessor == null) {
      return defaultMessage;
    }
    try {
      return messageAccessor.getMessage(
          code, new Object[0], defaultMessage, LocaleContextHolder.getLocale());
    } catch (NoSuchMessageException ignored) {
      return defaultMessage;
    }
  }
}
