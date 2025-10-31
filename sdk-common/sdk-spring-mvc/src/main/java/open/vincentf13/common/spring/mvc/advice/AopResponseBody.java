package open.vincentf13.common.spring.mvc.advice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.spring.mvc.config.MvcProperties;
import open.vincentf13.common.spring.mvc.OpenApiResponse;
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
public class AopResponseBody implements ResponseBodyAdvice<Object> {

    /** ObjectMapper 供 String 包裝時進行 JSON 序列化。 */
    private final ObjectMapper objectMapper;
    /** 自訂開關與忽略名單。 */
    private final MvcProperties properties;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 未開啟包裝或手動排除的 controller 直接跳過。
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
        if (body == null) { // 允許 GET 等無內容時仍回傳成功標準格式
            return OpenApiResponse.success();
        }
        if (body instanceof OpenApiResponse<?> || body instanceof org.springframework.http.ProblemDetail) { // 已是標準格式或 RFC7807 結構時不再包裝
            return body;
        }
        if (body instanceof byte[] || body instanceof Collection<?> || body.getClass().isArray()) { // 直接封裝在 data 內
            return OpenApiResponse.success(body);
        }
        if (body instanceof CharSequence) { // String 需手動序列化避免被當作純文字回傳
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return wrapString(body.toString(), selectedConverterType);
        }
        return OpenApiResponse.success(body);
    }

    private Object wrapString(String value, Class<? extends HttpMessageConverter<?>> converterType) {
        // 只對 Spring 的 StringHttpMessageConverter 進行 JSON 包裝，其餘 converter 採預設行為。
        if (StringHttpMessageConverter.class.isAssignableFrom(converterType)) {
            try {
                // 手動序列化避免 StringHttpMessageConverter 以純文字方式輸出，確保前端收到一致 JSON 結構。
                return objectMapper.writeValueAsString(OpenApiResponse.success(value));
            } catch (JsonProcessingException ex) {
                OpenLog.warn(log, "WrapStringResponseFailed", "Failed to wrap String response body",
                        ex,
                        "converter", converterType != null ? converterType.getName() : "unknown");
                return value;
            }
        }
        return OpenApiResponse.success(value);
    }
}
