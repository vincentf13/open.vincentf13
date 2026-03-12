package open.vincentf13.service.spot.infra.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
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

    /** 
      零分配序列化：將對象直接寫入 Netty ByteBuf，徹底消除 String 建立開銷 
     */
    public static void writeToByteBuf(ByteBuf buf, Object value) {
        try (ByteBufOutputStream os = new ByteBufOutputStream(buf)) {
            MAPPER.writeValue(os, value);
        } catch (IOException e) {
            log.error("JSON 序列化失敗: {}", e.getMessage());
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
            return MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
