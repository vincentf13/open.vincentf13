package open.vincentf13.sdk.spring.mvc.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.core.exception.OpenErrorCodeEnum;
import open.vincentf13.sdk.core.exception.OpenErrorCode;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
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
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AopRestException implements MessageSourceAware {

    private MessageSourceAccessor messageAccessor;

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageAccessor = new MessageSourceAccessor(messageSource);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                               WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        Map<String, Object> meta = baseMeta(servletRequest, HttpStatus.BAD_REQUEST);
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fieldError -> resolveMessage(fieldError, fieldError.getDefaultMessage()),
                        (left, right) -> right,
                        LinkedHashMap::new));
        meta.put("errors", errors);
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.REQUEST_VALIDATION_FAILED.code(),
                                                               resolveMessage("error.validation", OpenErrorCodeEnum.REQUEST_VALIDATION_FAILED.message()),
                                                               meta);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Object> handleBindException(BindException ex,
                                                      WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        Map<String, Object> meta = baseMeta(servletRequest, HttpStatus.BAD_REQUEST);
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fieldError -> resolveMessage(fieldError, fieldError.getDefaultMessage()),
                        (left, right) -> right,
                        LinkedHashMap::new));
        meta.put("errors", errors);
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.REQUEST_VALIDATION_FAILED.code(),
                                                               resolveMessage("error.validation", OpenErrorCodeEnum.REQUEST_VALIDATION_FAILED.message()),
                                                               meta);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                       WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        Map<String, Object> meta = baseMeta(servletRequest, HttpStatus.BAD_REQUEST);
        meta.put("parameter", ex.getParameterName());
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.REQUEST_PARAMETER_MISSING.code(),
                                                               resolveMessage("error.missing-parameter", OpenErrorCodeEnum.REQUEST_PARAMETER_MISSING.message()),
                                                               meta);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                               WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        String reason = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        OpenLog.debug(log, "HttpMessageUnreadable", () -> "Request payload unreadable",
                ex,
                "path", servletRequest != null ? servletRequest.getRequestURI() : "unknown",
                "reason", reason);
        Map<String, Object> meta = baseMeta(servletRequest, HttpStatus.BAD_REQUEST);
        meta.put("reason", reason);
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.REQUEST_PAYLOAD_UNREADABLE.code(),
                                                               resolveMessage("error.bad-request", OpenErrorCodeEnum.REQUEST_PAYLOAD_UNREADABLE.message()),
                                                               meta);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                      WebRequest request) {
        HttpServletRequest servletRequest = extractRequest(request);
        Map<String, Object> meta = baseMeta(servletRequest, HttpStatus.METHOD_NOT_ALLOWED);
        if (!CollectionUtils.isEmpty(ex.getSupportedHttpMethods())) {
            meta.put("supportedMethods", ex.getSupportedHttpMethods().stream().map(org.springframework.http.HttpMethod::name).toList());
        }
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.HTTP_METHOD_NOT_ALLOWED.code(),
                                                               resolveMessage("error.method-not-supported", OpenErrorCodeEnum.HTTP_METHOD_NOT_ALLOWED.message()),
                                                               meta);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    /**
     * 處理一般參數驗證錯誤（如 @Validated method-level）並回傳欄位錯誤明細。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex,
                                                                             HttpServletRequest request) {
        Map<String, Object> meta = baseMeta(request, HttpStatus.BAD_REQUEST);
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(AopRestException::violationPath,
                        violation -> violationMessage(violation),
                                          (left, right) -> right,
                                          LinkedHashMap::new));
        meta.put("errors", errors);
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.REQUEST_VALIDATION_FAILED.code(),
                                                               resolveMessage("error.validation", OpenErrorCodeEnum.REQUEST_VALIDATION_FAILED.message()),
                                                               meta);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * 處理業務層丟出的 OpenException，依錯誤碼映射 HTTP 狀態。
     */
    @ExceptionHandler(OpenException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleOpenException(OpenException ex,
                                                                       HttpServletRequest request) {
        HttpStatus status = mapStatus(ex.getCode());
        OpenLog.warn(log, "OpenException", "Open exception",
                ex,
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

    /**
     * 捕捉其他未預期例外，統一回傳 500 以免洩漏實作細節。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenApiResponse<Object>> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        OpenLog.error(log, "UnhandledException", "Unhandled exception",
                ex,
                "path", request != null ? request.getRequestURI() : "unknown");
        Map<String, Object> meta = baseMeta(request, HttpStatus.INTERNAL_SERVER_ERROR);
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.INTERNAL_ERROR.code(),
                                                               resolveMessage("error.internal", OpenErrorCodeEnum.INTERNAL_ERROR.message()),
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
            meta.put(OpenConstant.HttpHeader.TRACE_ID.value(), request.getAttribute(OpenConstant.HttpHeader.TRACE_ID.value()));
            meta.put(OpenConstant.HttpHeader.REQUEST_ID.value(), request.getAttribute(OpenConstant.HttpHeader.REQUEST_ID.value()));
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
        if (request instanceof org.springframework.web.context.request.NativeWebRequest nativeWebRequest) {
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

    private static String violationPath(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
    }
}
