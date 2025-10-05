package open.vincentf13.common.spring.mvc.advice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.common.spring.mvc.config.MvcProperties;
import open.vincentf13.common.spring.mvc.response.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Collection;

/**
 * 對 REST 回應做統一包裝，避免每支 API 重複建立標準格式。
 */
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;
    private final MvcProperties properties;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!properties.getResponse().isWrapEnabled()) {
            return false;
        }
        if (returnType == null) {
            return true;
        }
        Class<?> containingClass = returnType.getContainingClass();
        if (containingClass != null) {
            for (String prefix : properties.getResponse().getIgnoreControllerPrefixes()) {
                if (StringUtils.hasText(prefix) && containingClass.getName().startsWith(prefix)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!properties.getResponse().isWrapEnabled()) {
            return body;
        }
        if (returnType != null && ResponseEntity.class.isAssignableFrom(returnType.getParameterType())) {
            return body;
        }
        if (body == null) {
            return ApiResponse.success();
        }
        if (body instanceof ApiResponse<?> || body instanceof org.springframework.http.ProblemDetail) {
            return body;
        }
        if (body instanceof byte[] || body instanceof Collection<?> || body.getClass().isArray()) {
            return ApiResponse.success(body);
        }
        if (body instanceof CharSequence) {
            return wrapString(body.toString(), selectedConverterType);
        }
        return ApiResponse.success(body);
    }

    private Object wrapString(String value, Class<? extends HttpMessageConverter<?>> converterType) {
        if (StringHttpMessageConverter.class.isAssignableFrom(converterType)) {
            try {
                // 手動序列化避免 StringHttpMessageConverter 以純文字方式輸出，確保前端收到一致 JSON 結構。
                return objectMapper.writeValueAsString(ApiResponse.success(value));
            } catch (JsonProcessingException ex) {
                log.warn("Failed to wrap String response body", ex);
                return value;
            }
        }
        return ApiResponse.success(value);
    }
}
