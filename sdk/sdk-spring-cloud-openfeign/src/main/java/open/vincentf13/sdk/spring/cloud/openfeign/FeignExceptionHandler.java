package open.vincentf13.sdk.spring.cloud.openfeign;

import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;
import feign.codec.EncodeException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import open.vincentf13.sdk.core.OpenConstant;
import open.vincentf13.sdk.core.exception.OpenErrorCodeEnum;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全域 Feign 例外處理器，統一封裝遠端呼叫異常的 API 回應格式。
 */
@RestControllerAdvice
@ConditionalOnClass(FeignException.class)
public class FeignExceptionHandler implements MessageSourceAware {

    private static final Logger log = LoggerFactory.getLogger(FeignExceptionHandler.class);

    private MessageSourceAccessor messageAccessor;

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageAccessor = new MessageSourceAccessor(messageSource);
    }

    /**
     * Circuit breaker currently open (Resilience4j).
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleCallNotPermitted(CallNotPermittedException ex,
                                                                          HttpServletRequest request) {
        String breakerName = resolveCircuitBreakerName(ex);
        OpenLog.warn(log, "FeignCircuitBreakerOpen", "Circuit breaker prevented remote call",
                ex,
                "circuitBreaker", breakerName);
        Map<String, Object> meta = baseMeta(request, HttpStatus.SERVICE_UNAVAILABLE);
        if (StringUtils.hasText(breakerName)) {
            meta.put("circuitBreaker", breakerName);
        }
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.REMOTE_SERVICE_UNAVAILABLE.code(),
                                                               resolveMessage("error.remote.unavailable", OpenErrorCodeEnum.REMOTE_SERVICE_UNAVAILABLE.message()),
                                                               meta);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Retryable Feign exception (timeouts, transient network issues).
     */
    @ExceptionHandler(RetryableException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleRetryable(RetryableException ex, HttpServletRequest request) {
        OpenLog.warn(log, "FeignRetryableException", "Retryable remote call failure",
                ex,
                "target", ex.request() != null ? ex.request().url() : "unknown");
        Map<String, Object> meta = baseMeta(request, HttpStatus.SERVICE_UNAVAILABLE);
        enrichWithFeignRequest(meta, ex);
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.REMOTE_SERVICE_UNAVAILABLE.code(),
                                                               resolveMessage("error.remote.retryable", OpenErrorCodeEnum.REMOTE_SERVICE_UNAVAILABLE.message()),
                                                               meta);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Non-retryable Feign exception (HTTP errors, etc.).
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleFeign(FeignException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.status());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        OpenLog.warn(log, "FeignException", "Feign call failed",
                ex,
                "status", ex.status(),
                "target", ex.request() != null ? ex.request().url() : "unknown");
        Map<String, Object> meta = baseMeta(request, status);
        enrichWithFeignRequest(meta, ex);
        meta.put("feignStatus", ex.status());
        if (StringUtils.hasText(ex.getMessage())) {
            meta.put("reason", ex.getMessage());
        }
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.REMOTE_SERVICE_ERROR.code(),
                                                               resolveMessage("error.remote.failure", OpenErrorCodeEnum.REMOTE_SERVICE_ERROR.message()),
                                                               meta);
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Feign response decoding failure.
     */
    @ExceptionHandler(DecodeException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleDecode(DecodeException ex, HttpServletRequest request) {
        OpenLog.warn(log, "FeignDecodeException", "Failed to decode remote response",
                ex);
        Map<String, Object> meta = baseMeta(request, HttpStatus.BAD_GATEWAY);
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.REMOTE_RESPONSE_DECODE_FAILED.code(),
                                                               resolveMessage("error.remote.decode", OpenErrorCodeEnum.REMOTE_RESPONSE_DECODE_FAILED.message()),
                                                               meta);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    /**
     * Feign request encoding failure.
     */
    @ExceptionHandler(EncodeException.class)
    public ResponseEntity<OpenApiResponse<Object>> handleEncode(EncodeException ex, HttpServletRequest request) {
        OpenLog.warn(log, "FeignEncodeException", "Failed to encode remote request",
                ex);
        Map<String, Object> meta = baseMeta(request, HttpStatus.BAD_REQUEST);
        OpenApiResponse<Object> body = OpenApiResponse.failure(OpenErrorCodeEnum.REMOTE_REQUEST_ENCODE_FAILED.code(),
                                                               resolveMessage("error.remote.encode", OpenErrorCodeEnum.REMOTE_REQUEST_ENCODE_FAILED.message()),
                                                               meta);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private Map<String, Object> baseMeta(@Nullable HttpServletRequest request, HttpStatus status) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("status", status.value());
        meta.put("timestamp", Instant.now().toString());
        if (request != null) {
            meta.put("path", request.getRequestURI());
            meta.put("method", request.getMethod());
            meta.put(OpenConstant.HttpHeader.TRACE_ID.value(), request.getAttribute(OpenConstant.HttpHeader.TRACE_ID.value()));
            meta.put(OpenConstant.HttpHeader.REQUEST_ID.value(), request.getAttribute(OpenConstant.HttpHeader.REQUEST_ID.value()));
        }
        return meta;
    }

    private String resolveCircuitBreakerName(CallNotPermittedException ex) {
        try {
            var method = CallNotPermittedException.class.getMethod("getCircuitBreakerName");
            Object value = method.invoke(ex);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException ignored) {
            return ex.getMessage();
        }
    }

    private void enrichWithFeignRequest(Map<String, Object> meta, FeignException ex) {
        if (ex.request() == null) {
            return;
        }
        meta.put("remoteUrl", ex.request().url());
        if (ex.request().httpMethod() != null) {
            meta.put("remoteMethod", ex.request().httpMethod().name());
        }
    }

    private String resolveMessage(String code, String defaultMessage) {
        if (messageAccessor == null) {
            return defaultMessage;
        }
        try {
            return messageAccessor.getMessage(code, new Object[0], defaultMessage, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException ignored) {
            return defaultMessage;
        }
    }
}
