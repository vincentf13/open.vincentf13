package open.vincentf13.sdk.spring.mvc.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.mvc.log.MvcEventEnum;
import open.vincentf13.sdk.spring.mvc.config.MvcProperties;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
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
@RestControllerAdvice
@ConditionalOnClass(ResponseBodyAdvice.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AopResponseBody implements ResponseBodyAdvice<Object> {

    /** ObjectMapper 供 String 包裝時進行 JSON 序列化。 */
    private final ObjectMapper objectMapper;
    /** 自訂開關與忽略名單。 */
    private final MvcProperties properties;

    public AopResponseBody(ObjectMapper objectMapper, MvcProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

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
        // 全域回應包裝開關也會在 supports() 判斷，但此處再次檢查可避免外部程式直接呼叫 beforeBodyWrite 時未帶入 supports 結果。
        if (!properties.getResponse().isWrapEnabled()) {
            return body;
        }
        // ResponseEntity 表示應用層已自行包裝（含狀態碼與 headers），尊重既有格式直接返回。
        if (returnType != null && ResponseEntity.class.isAssignableFrom(returnType.getParameterType())) {
            return body;
        }
        if (body == null) { // 允許 GET/DELETE 等無內容回傳時仍回傳標準成功格式
            return OpenApiResponse.success();
        }
        // 已是標準格式（OpenApiResponse）或 RFC7807 標準錯誤格式時，不再重複包裝。
        if (body instanceof OpenApiResponse<?> || body instanceof org.springframework.http.ProblemDetail) {
            return body;
        }
        // 任何集合、陣列或 byte[] 直接塞進 data 內即可。
        if (body instanceof byte[] || body instanceof Collection<?> || body.getClass().isArray()) {
            return OpenApiResponse.success(body);
        }
        // String 回傳需改寫成 JSON，避免 StringHttpMessageConverter 以 text/plain 輸出。
        if (body instanceof CharSequence) {
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
                OpenLog.warn( MvcEventEnum.WRAP_STRING_RESPONSE_FAILED,
                        ex,
                        "converter", converterType != null ? converterType.getName() : "unknown");
                return value;
            }
        }
        return OpenApiResponse.success(value);
    }
}
