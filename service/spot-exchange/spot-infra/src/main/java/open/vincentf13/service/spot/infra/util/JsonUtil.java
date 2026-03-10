package open.vincentf13.service.spot.infra.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper()
                                                       .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    
    public static JsonNode readTree(String content) {
        try {
            return MAPPER.readTree(content);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     將 JSON 更新到現有的物件實例中 (Zero-GC 關鍵)
     */
    public static void updateObject(String content,
                                    Object target) {
        try {
            MAPPER.readerForUpdating(target).readValue(content);
        } catch (Exception e) {
            log.error("Update Object Error: {}", e.getMessage());
        }
    }
    
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    public static String toJson(String topic,
                                Object data) {
        return toJson(Map.of("topic", topic, "data", data));
    }
}
