package open.vincentf13.service.spot.infra.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/** 
  JSON 靜態工具類
  全系統統一使用單例 ObjectMapper 以減少記憶體分配
 */
@Slf4j
public class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JsonNode readTree(String content) {
        try {
            return MAPPER.readTree(content);
        } catch (Exception e) {
            log.error("JSON parse error: {}", e.getMessage());
            return null;
        }
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            log.error("JSON serialize error: {}", e.getMessage());
            return "{}";
        }
    }

    public static String toJson(String topic, Object data) {
        return toJson(Map.of("topic", topic, "data", data));
    }
}
