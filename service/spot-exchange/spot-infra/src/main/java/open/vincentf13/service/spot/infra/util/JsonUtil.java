package open.vincentf13.service.spot.infra.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final JsonFactory FACTORY = MAPPER.getFactory();

    /** 預分配 TypeReference：消除每次調用 toMap 時的匿名內部類分配 */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Envelope {
        private String topic;
        private Object data;
    }

    public static JsonParser createParser(ByteBuf buf) throws IOException {
        return FACTORY.createParser(new ByteBufInputStream(buf));
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static Map<String, Object> toMap(String json) {
        try {
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateObject(String content, Object target) {
        try {
            MAPPER.readerForUpdating(target).readValue(content);
        } catch (Exception e) {
            log.error("Update Object Error: {}", e.getMessage());
        }
    }
}
