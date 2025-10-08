package open.vincentf13.common.spring.mvc.advice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.common.core.exception.BackendErrorCodes;
import open.vincentf13.common.core.exception.ControllerException;
import open.vincentf13.common.core.log.FastLog;
import open.vincentf13.common.spring.mvc.response.ApiResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring MVC 統一例外處理，轉換成統一的 {@link ApiResponse} 物件。
 */
@Slf4j
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler implements MessageSourceAware {

    private MessageSourceAccessor messageAccessor;

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageAccessor = new MessageSourceAccessor(messageSource);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        Map<String, Object> meta = baseMeta(servletRequest, HttpStatus.BAD_REQUEST);
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fieldError -> resolveMessage(fieldError, fieldError.getDefaultMessage()),
                        (left, right) -> right,
                        LinkedHashMap::new));
        meta.put("errors", errors);
        ApiResponse<Object> body = ApiResponse.failure(BackendErrorCodes.REQUEST_VALIDATION_FAILED.code(),
                resolveMessage("error.validation", BackendErrorCodes.REQUEST_VALIDATION_FAILED.message()),
                meta);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleBindException(BindException ex,
                                                         HttpHeaders headers,
                                                         HttpStatusCode status,
                                                         WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        Map<String, Object> meta = baseMeta(servletRequest, HttpStatus.BAD_REQUEST);
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fieldError -> resolveMessage(fieldError, fieldError.getDefaultMessage()),
                        (left, right) -> right,
                        LinkedHashMap::new));
        meta.put("errors", errors);
        ApiResponse<Object> body = ApiResponse.failure(BackendErrorCodes.REQUEST_VALIDATION_FAILED.code(),
                resolveMessage("error.validation", BackendErrorCodes.REQUEST_VALIDATION_FAILED.message()),
                meta);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        Map<String, Object> meta = baseMeta(servletRequest, HttpStatus.BAD_REQUEST);
        meta.put("parameter", ex.getParameterName());
        ApiResponse<Object> body = ApiResponse.failure(BackendErrorCodes.REQUEST_PARAMETER_MISSING.code(),
                resolveMessage("error.missing-parameter", BackendErrorCodes.REQUEST_PARAMETER_MISSING.message()),
                meta);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        String reason = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        FastLog.debug(log, "HttpMessageUnreadable", () -> "Request payload unreadable",
                ex,
                "path", servletRequest != null ? servletRequest.getRequestURI() : "unknown",
                "reason", reason);
        Map<String, Object> meta = baseMeta(servletRequest, HttpStatus.BAD_REQUEST);
        meta.put("reason", reason);
        ApiResponse<Object> body = ApiResponse.failure(BackendErrorCodes.REQUEST_PAYLOAD_UNREADABLE.code(),
                resolveMessage("error.bad-request", BackendErrorCodes.REQUEST_PAYLOAD_UNREADABLE.message()),
                meta);
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                         HttpHeaders headers,
                                                                         HttpStatusCode status,
                                                                         WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        Map<String, Object> meta = baseMeta(servletRequest, HttpStatus.METHOD_NOT_ALLOWED);
        if (!CollectionUtils.isEmpty(ex.getSupportedHttpMethods())) {
            meta.put("supportedMethods", ex.getSupportedHttpMethods().stream().map(org.springframework.http.HttpMethod::name).toList());
        }
        ApiResponse<Object> body = ApiResponse.failure(BackendErrorCodes.HTTP_METHOD_NOT_ALLOWED.code(),
                resolveMessage("error.method-not-supported", BackendErrorCodes.HTTP_METHOD_NOT_ALLOWED.message()),
                meta);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    /**
     * 處理一般參數驗證錯誤（如 @Validated method-level）並回傳欄位錯誤明細。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex,
                                                                         HttpServletRequest request) {
        Map<String, Object> meta = baseMeta(request, HttpStatus.BAD_REQUEST);
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(RestExceptionHandler::violationPath,
                        violation -> violationMessage(violation),
                        (left, right) -> right,
                        LinkedHashMap::new));
        meta.put("errors", errors);
        ApiResponse<Object> body = ApiResponse.failure(BackendErrorCodes.REQUEST_VALIDATION_FAILED.code(),
                resolveMessage("error.validation", BackendErrorCodes.REQUEST_VALIDATION_FAILED.message()),
                meta);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 將業務層主動拋出的 ControllerException 轉為 500 響應。
     */
    @ExceptionHandler(ControllerException.class)
    public ResponseEntity<ApiResponse<Object>> handleControllerException(ControllerException ex,
                                                                         HttpServletRequest request) {
        FastLog.warn(log, "ControllerException", "Controller exception",
                ex,
                "code", ex.getCode(),
                "path", request != null ? request.getRequestURI() : "unknown");
        Map<String, Object> meta = baseMeta(request, HttpStatus.INTERNAL_SERVER_ERROR);
        if (!CollectionUtils.isEmpty(ex.getMeta())) {
            meta.putAll(ex.getMeta());
        }
        ApiResponse<Object> body = ApiResponse.failure(ex.getCode(), ex.getMessage(), meta);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * 捕捉其他未預期例外，統一回傳 500 以免洩漏實作細節。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        FastLog.error(log, "UnhandledException", "Unhandled exception",
                ex,
                "path", request != null ? request.getRequestURI() : "unknown");
        Map<String, Object> meta = baseMeta(request, HttpStatus.INTERNAL_SERVER_ERROR);
        ApiResponse<Object> body = ApiResponse.failure(BackendErrorCodes.INTERNAL_ERROR.code(),
                resolveMessage("error.internal", BackendErrorCodes.INTERNAL_ERROR.message()),
                meta);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * 組裝統一的 meta 段落，方便客戶端除錯（狀態碼、時間戳、請求識別）。
     */
    private Map<String, Object> baseMeta(@Nullable HttpServletRequest request, HttpStatus status) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("status", status.value());
        meta.put("timestamp", Instant.now().toString());
        if (request != null) {
            // 使用 attribute 而非重新生成，確保與 RequestCorrelationFilter 一致。
            meta.put("path", request.getRequestURI());
            meta.put("method", request.getMethod());
            meta.put("traceId", request.getAttribute("traceId"));
            meta.put("requestId", request.getAttribute("requestId"));
        }
        return meta;
    }

    /**
     * 從不同的 WebRequest 實作安全取得 HttpServletRequest 以提取請求資訊。
     */
    private HttpServletRequest extractRequest(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest();
        }
        if (request instanceof NativeWebRequest nativeWebRequest) {
            return nativeWebRequest.getNativeRequest(HttpServletRequest.class);
        }
        return null;
    }

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

    private String resolveMessage(String code, String defaultMessage) {
        if (messageAccessor == null) {
            return defaultMessage;
        }
        return messageAccessor.getMessage(code, new Object[0], defaultMessage, LocaleContextHolder.getLocale());
    }

    private String violationMessage(ConstraintViolation<?> violation) {
        String template = violation.getMessageTemplate();
        if (template != null && template.startsWith("{") && template.endsWith("}")) {
            String code = template.substring(1, template.length() - 1);
            return resolveMessage(code, violation.getMessage());
        }
        return violation.getMessage();
    }

    private static String violationPath(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
    }
}
