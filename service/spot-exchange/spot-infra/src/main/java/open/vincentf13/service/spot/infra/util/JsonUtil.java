package open.vincentf13.service.spot.infra.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final JsonFactory FACTORY = MAPPER.getFactory();

    public static JsonParser createParser(ByteBuf buf) throws IOException {
        return FACTORY.createParser(new ByteBufInputStream(buf));
    }

    public static JsonNode readTree(String content) {
        try {
            return MAPPER.readTree(content);
        } catch (Exception e) {
            return null;
        }
    }
    
    public static void updateObject(String content, Object target) {
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
    
    public static Map<String, Object> toMap(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String toJson(String topic, Object data) {
        return toJson(Map.of("topic", topic, "data", data));
    }
}
