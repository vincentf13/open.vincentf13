package open.vincentf13.common.spring.mvc.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * REST 統一回應封裝。預設 code=0、message=OK，錯誤時帶入自訂代碼與訊息。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        Instant timestamp,
        Map<String, Object> meta
) {
    private static final String SUCCESS_CODE = "0";
    private static final String SUCCESS_MESSAGE = "OK";

    public static <T> ApiResponse<T> success(T payload) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, payload, Instant.now(), null);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, null, Instant.now(), null);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now(), null);
    }

    public static <T> ApiResponse<T> failure(String code, String message, Map<String, Object> meta) {
        return new ApiResponse<>(code, message, null, Instant.now(), normalize(meta));
    }

    public ApiResponse<T> withMeta(Map<String, Object> additional) {
        return new ApiResponse<>(code, message, data, timestamp, merge(meta, additional));
    }

    private static Map<String, Object> normalize(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return Map.copyOf(source);
    }

    private static Map<String, Object> merge(Map<String, Object> current, Map<String, Object> incoming) {
        if ((current == null || current.isEmpty()) && (incoming == null || incoming.isEmpty())) {
            return null;
        }
        if (current == null || current.isEmpty()) {
            return normalize(incoming);
        }
        if (incoming == null || incoming.isEmpty()) {
            return Map.copyOf(current);
        }
        var merged = new java.util.LinkedHashMap<String, Object>(current.size() + incoming.size());
        // 採用 LinkedHashMap 保留原有 meta 項目順序，避免前端序列化後字段跳動。
        merged.putAll(current);
        merged.putAll(incoming);
        return Map.copyOf(merged);
    }

    public boolean isSuccess() {
        return Objects.equals(SUCCESS_CODE, code);
    }
}
