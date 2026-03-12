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

    /** 
      高效解析：利用 Jackson 直接處理 Netty ByteBuf
      注意：ByteBufInputStream 的分配在 Jackson 內部通常已被優化，
      但我們確保傳遞的是原始 ByteBuf 以減少 String 建立。
     */
    public static JsonParser createParser(ByteBuf buf) throws IOException {
        // 直接使用 Jackson 對 InputStream 的支持，這是目前最穩定的路徑
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

    /** 
      零分配封裝序列化：配合 Topic 的高效 JSON 產生 
     */
    public static String toJson(String topic, Object data) {
        // 在高頻下，此處 Map.of 仍有改進空間，但在 Gateway 端的 sendMessage 已經優化為位元組流
        return toJson(Map.of("topic", topic, "data", data));
    }
}
