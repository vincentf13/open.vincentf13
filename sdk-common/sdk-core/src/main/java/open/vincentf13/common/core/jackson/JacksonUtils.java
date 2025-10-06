package open.vincentf13.common.core.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Jackson JSON 編解碼工具
 */
@Component
public final class JacksonUtils {

    public static final class JsonCodecException extends RuntimeException {
        public JsonCodecException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    private final ObjectMapper mapper;
    private final ObjectWriter prettyWriter;

    public JacksonUtils(@Qualifier("jsonMapper") ObjectMapper jsonMapper) {
        this.mapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.prettyWriter = this.mapper.writerWithDefaultPrettyPrinter();
    }

    /**
     * 物件 → JSON 字串
     */
    public String toJson(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to serialize object to JSON", ex);
        }
    }

    /**
     * 物件 → 格式化 JSON 字串
     */
    public String toPrettyJson(Object value) {
        if (value == null) return null;
        try {
            return prettyWriter.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to serialize object to pretty JSON", ex);
        }
    }

    /**
     * 物件 → JSON bytes (UTF-8)
     */
    public byte[] toBytes(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to serialize object to JSON bytes", ex);
        }
    }

    /**
     * JSON → 物件
     */
    public <T> T fromJson(String json, Class<T> type) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to deserialize JSON", ex);
        }
    }

    public <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to deserialize JSON", ex);
        }
    }

    /**
     * JSON bytes → 物件
     */
    public <T> T fromBytes(byte[] jsonBytes, Class<T> type) {
        if (jsonBytes == null) return null;
        try {
            return mapper.readValue(jsonBytes, type);
        } catch (IOException ex) {
            throw new JsonCodecException("Failed to deserialize JSON bytes", ex);
        }
    }

    /**
     * JSON bytes → 泛型物件
     */
    public <T> T fromBytes(byte[] jsonBytes, TypeReference<T> type) {
        if (jsonBytes == null) return null;
        try {
            return mapper.readValue(jsonBytes, type);
        } catch (IOException ex) {
            throw new JsonCodecException("Failed to deserialize JSON bytes", ex);
        }
    }

    /**
     * 讀取為樹節點 JsonNode
     */
    public JsonNode readTree(String json) {
        if (json == null) return null;
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to read JSON tree", ex);
        }
    }

    /**
     * 物件 → JsonNode
     */
    public JsonNode toNode(Object value) {
        if (value == null) return null;
        return mapper.valueToTree(value);
    }

    /**
     * 物件轉換：DTO ↔ VO ↔ Map
     * UserDto dto = convert(user, UserDto.class);
     * Map<String,Object> map = convert(user, new TypeReference<Map<String,Object>>(){});
     */
    public <T> T convert(Object src, Class<T> targetType) {
        if (src == null) return null;
        return mapper.convertValue(src, targetType);
    }

    /**
     * 類型轉換：支援泛型目標
     */
    public <T> T convert(Object src, TypeReference<T> targetType) {
        if (src == null) return null;
        return mapper.convertValue(src, targetType);
    }

    /**
     * 合併 JSON 到既有物件（部分更新）
     * User user = new User(1, "Tom");
     * update(user, "{\"name\":\"Jerry\"}");
     * user.name → Jerry
     */
    public <T> T update(T target, String patchJson) {
        if (target == null || patchJson == null) return target;
        try {
            ObjectReader updatingReader = mapper.readerForUpdating(target);
            return updatingReader.readValue(patchJson);
        } catch (IOException ex) {
            throw new JsonCodecException("Failed to update target with JSON", ex);
        }
    }

    /**
     * 粗略校驗字串是否為 JSON
     */
    public boolean isJson(String text) {
        if (text == null) return false;
        try {
            mapper.readTree(text);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * UTF-8 bytes → String，便於手動處理
     */
    public static String utf8(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }
}