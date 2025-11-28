package open.vincentf13.sdk.spring.mvc.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.core.exception.OpenErrorCode;
import open.vincentf13.sdk.core.exception.OpenErrorCodes;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.mvc.MvcEvent;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring MVC 統一例外處理，轉換成統一的 {@link OpenApiResponse} 物件。
 */
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OpenRestExceptionAdvice implements MessageSourceAware {

    private MessageSourceAccessor messageAccessor;

    /*
      注入 MessageSource 用於國際化訊息解析
     */
    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageAccessor = new MessageSourceAccessor(messageSource);
    }

    /*
      處理 Bean Validation 錯誤（@Valid, @Validated）
      - 統一處理 MethodArgumentNotValidException 和 BindException
      - 提取所有欄位驗證錯誤並以 Map 形式回傳
      - 回傳 400 BAD_REQUEST
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Object> handleValidationErrors(Exception ex, WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        Map<String, String> fieldErrors = ex instanceof MethodArgumentNotValidException methodEx
                ? extractFieldErrors(methodEx.getBindingResult().getFieldErrors())
                : extractFieldErrors(((BindException) ex).getBindingResult().getFieldErrors());

        return buildErrorResponse(servletRequest, HttpStatus.BAD_REQUEST,
                OpenErrorCodes.REQUEST_VALIDATION_FAILED, "error.validation",
                Map.of("errors", fieldErrors));
    }

    /*
      處理缺少必要請求參數的錯誤
      - 當必要的查詢參數或表單參數未提供時觸發
      - 回傳 400 BAD_REQUEST
      - metadata 中包含缺少的參數名稱
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                       WebRequest request) {
        return buildErrorResponse(extractRequest(request), HttpStatus.BAD_REQUEST,
                OpenErrorCodes.REQUEST_PARAMETER_MISSING, "error.missing-parameter",
                Map.of("parameter", ex.getParameterName()));
    }

    /*
      處理 HTTP 請求體無法讀取或解析的錯誤
      - 常見於 JSON 格式錯誤、類型不匹配等
      - 記錄詳細錯誤原因到 debug 日誌
      - 回傳 400 BAD_REQUEST
      - metadata 中包含錯誤原因
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                               WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        String reason = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        OpenLog.debug(MvcEvent.HTTP_MESSAGE_UNREADABLE,
                "path", servletRequest != null ? servletRequest.getRequestURI() : "unknown",
                "reason", reason);
        return buildErrorResponse(servletRequest, HttpStatus.BAD_REQUEST,
                OpenErrorCodes.REQUEST_PAYLOAD_UNREADABLE, "error.bad-request",
                Map.of("reason", reason));
    }

    /*
      處理不支援的 HTTP 方法錯誤
      - 例如對 GET-only 端點使用 POST
      - 回傳 405 METHOD_NOT_ALLOWED
      - metadata 中列出該端點支援的 HTTP 方法清單
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                      WebRequest request) {
        Map<String, Object> additionalMeta = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(ex.getSupportedHttpMethods())) {
            additionalMeta.put("supportedMethods", ex.getSupportedHttpMethods().stream()
                    .map(org.springframework.http.HttpMethod::name).toList());
        }
        return buildErrorResponse(extractRequest(request), HttpStatus.METHOD_NOT_ALLOWED,
                OpenErrorCodes.HTTP_METHOD_NOT_ALLOWED, "error.method-not-supported",
                additionalMeta);
    }

    /*
      處理方法層級的參數驗證錯誤
      - 處理 @Validated 標註在 Controller 類上時的方法參數驗證
      - 提取所有約束違規並轉換為欄位名稱到錯誤訊息的 Map
      - 回傳 400 BAD_REQUEST
      - metadata 中包含所有驗證錯誤
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex,
                                                             HttpServletRequest request) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(OpenRestExceptionAdvice::violationPath,
                                          this::violationMessage,
                                          (left, right) -> right,
                                          LinkedHashMap::new));
        return buildErrorResponse(request, HttpStatus.BAD_REQUEST,
                OpenErrorCodes.REQUEST_VALIDATION_FAILED, "error.validation",
                Map.of("errors", errors));
    }

    /*
      處理斷路器開啟的例外（Resilience4j）
      - 當服務斷路器開啟，拒絕呼叫時觸發
      - 記錄斷路器名稱到日誌
      - 回傳 503 SERVICE_UNAVAILABLE
      - metadata 中包含斷路器名稱
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<Object> handleCallNotPermitted(CallNotPermittedException ex,
                                                          HttpServletRequest request) {
        String breakerName = resolveCircuitBreakerName(ex);
        OpenLog.warn(MvcEvent.CIRCUIT_BREAKER_OPEN, ex, "circuitBreaker", breakerName);
        Map<String, Object> additionalMeta = new LinkedHashMap<>();
        if (StringUtils.hasText(breakerName)) {
            additionalMeta.put("circuitBreaker", breakerName);
        }
        return buildErrorResponse(request, HttpStatus.SERVICE_UNAVAILABLE,
                OpenErrorCodes.REMOTE_SERVICE_UNAVAILABLE, "error.remote.unavailable",
                additionalMeta);
    }

    /*
      處理業務層丟出的 OpenException
      - 從錯誤碼格式（如 "Service-404-1001"）中解析第二段作為 HTTP 狀態碼
      - 記錄 WARN 等級日誌，包含錯誤碼和請求路徑
      - 合併例外中攜帶的自訂 metadata
      - 根據錯誤碼動態決定 HTTP 狀態碼
     */
    @ExceptionHandler(OpenException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleOpenException(OpenException ex,
                                                                       HttpServletRequest request) {
        HttpStatus status = mapStatus(ex.getCode());
        OpenLog.warn( MvcEvent.OPEN_EXCEPTION, ex,
                "code", ex.getCode() != null ? ex.getCode().code() : null,
                "path", request != null ? request.getRequestURI() : "unknown");
        Map<String, Object> meta = baseMeta(request, status);
        if (!CollectionUtils.isEmpty(ex.getMeta())) {
            meta.putAll(ex.getMeta());
        }
        String code = ex.getCode() != null ? ex.getCode().code() : null;
        OpenApiResponse<Object> body = OpenApiResponse.failure(code, ex.getMessage(), meta);
        return ResponseEntity.status(status).body(body);
    }

    /*
      捕捉所有未被其他 Handler 處理的例外
      - 作為最後的安全網，捕捉所有未預期的例外
      - 記錄 ERROR 等級日誌，包含完整 stack trace
      - 統一回傳 500 INTERNAL_SERVER_ERROR
      - 避免向客戶端洩漏內部實作細節或敏感資訊
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenApiResponse<Object>> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        OpenLog.error( MvcEvent.UNHANDLED_EXCEPTION, ex,
                "path", request != null ? request.getRequestURI() : "unknown");
        Map<String, Object> meta = baseMeta(request, HttpStatus.INTERNAL_SERVER_ERROR);
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodes.INTERNAL_ERROR.code(),
                                                               resolveMessage("error.internal", OpenErrorCodes.INTERNAL_ERROR.message()),
                                                               meta);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /*
      提取欄位驗證錯誤並轉換成 Map
      - 將 FieldError 列表轉換為欄位名稱到錯誤訊息的 Map
      - 使用 MessageSource 解析國際化訊息
      - 保持插入順序（使用 LinkedHashMap）
     */
    private Map<String, String> extractFieldErrors(java.util.List<FieldError> fieldErrors) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : fieldErrors) {
            errors.put(error.getField(), resolveMessage(error, error.getDefaultMessage()));
        }
        return errors;
    }

    /*
      建立統一格式的錯誤回應
      - 組裝基礎 metadata（status, timestamp, path, traceId 等）
      - 合併額外的 metadata
      - 解析國際化錯誤訊息
      - 封裝成 OpenApiResponse 並包裝在 ResponseEntity 中
     */
    private ResponseEntity<Object> buildErrorResponse(HttpServletRequest request,
                                                       HttpStatus status,
                                                       OpenErrorCode errorCode,
                                                       String messageCode,
                                                       Map<String, Object> additionalMeta) {
        Map<String, Object> meta = baseMeta(request, status);
        if (additionalMeta != null && !additionalMeta.isEmpty()) {
            meta.putAll(additionalMeta);
        }
        OpenApiResponse<Object> body = OpenApiResponse.failure(
                errorCode.code(),
                resolveMessage(messageCode, errorCode.message()),
                meta);
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
            // 使用 attribute 而非重新生成，確保與 RequestCorrelationFilter 一致。
            meta.put("path", request.getRequestURI());
            meta.put("method", request.getMethod());
            meta.put(OpenConstant.HttpHeader.TRACE_ID.value(), request.getAttribute(OpenConstant.HttpHeader.TRACE_ID.value()));
            meta.put(OpenConstant.HttpHeader.REQUEST_ID.value(), request.getAttribute(OpenConstant.HttpHeader.REQUEST_ID.value()));
        }
        return meta;
    }

    /*
      從 WebRequest 中安全提取 HttpServletRequest
      - 處理 ServletWebRequest 和 NativeWebRequest 兩種實作
      - 若無法提取則回傳 null
     */
    private HttpServletRequest extractRequest(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest();
        }
        if (request instanceof org.springframework.web.context.request.NativeWebRequest nativeWebRequest) {
            return nativeWebRequest.getNativeRequest(HttpServletRequest.class);
        }
        return null;
    }

    /*
      解析 FieldError 的國際化訊息
      - 優先從 MessageSource 中根據當前 Locale 解析訊息
      - 若無 MessageSource 或找不到訊息，則使用預設訊息
     */
    private String resolveMessage(FieldError fieldError, String defaultMessage) {
        if (messageAccessor == null) {
            return defaultMessage;
        }
        try {
            return messageAccessor.getMessage(fieldError, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException ignored) {
            return defaultMessage;
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
        return messageAccessor.getMessage(code, new Object[0], defaultMessage, LocaleContextHolder.getLocale());
    }

    /*
      提取約束違規的錯誤訊息
      - 若訊息模板為 {code} 格式，則解析為國際化訊息
      - 否則直接使用約束違規的原始訊息
     */
    private String violationMessage(ConstraintViolation<?> violation) {
        String template = violation.getMessageTemplate();
        if (template != null && template.startsWith("{") && template.endsWith("}")) {
            String code = template.substring(1, template.length() - 1);
            return resolveMessage(code, violation.getMessage());
        }
        return violation.getMessage();
    }

    /*
      從 OpenErrorCode 中解析對應的 HTTP 狀態碼
      - 錯誤碼格式為 "ServiceName-StatusCode-SequenceNumber"（例如 "Ledger-404-1001"）
      - 提取第二段（StatusCode）並轉換為 HttpStatus
      - 若格式不正確或無法解析，預設回傳 500 INTERNAL_SERVER_ERROR
     */
    private HttpStatus mapStatus(OpenErrorCode code) {
        if (code == null || code.code() == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String[] segments = code.code().split("-");
        if (segments.length < 2) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        try {
            int statusCode = Integer.parseInt(segments[1]);
            return HttpStatus.resolve(statusCode) != null ? HttpStatus.valueOf(statusCode) : HttpStatus.INTERNAL_SERVER_ERROR;
        } catch (NumberFormatException ex) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /*
      提取約束違規的屬性路徑
      - 將 PropertyPath 轉換為字串（例如 "user.email"）
      - 若 PropertyPath 為 null，則回傳空字串
     */
    private static String violationPath(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
    }

    /*
      解析斷路器名稱
      - 透過反射取得 Resilience4j 斷路器名稱
      - 若反射失敗則回傳例外訊息
     */
    private String resolveCircuitBreakerName(CallNotPermittedException ex) {
        try {
            var method = CallNotPermittedException.class.getMethod("getCircuitBreakerName");
            Object value = method.invoke(ex);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException ignored) {
            return ex.getMessage();
        }
    }
}
