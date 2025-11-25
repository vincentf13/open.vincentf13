package open.vincentf13.sdk.core.object.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * OpenObjectMapper：集中管理 JSON 編解碼的靜態工具。
 */
public final class OpenObjectMapper {

    public static final class JsonCodecException extends RuntimeException {
        public JsonCodecException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    private static volatile ObjectMapper mapper;
    private static volatile ObjectWriter prettyWriter;

    private OpenObjectMapper() {
    }

    public static void register(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        mapper = objectMapper;
        prettyWriter = objectMapper.writerWithDefaultPrettyPrinter();
    }

    private static ObjectMapper mapper() {
        ObjectMapper m = mapper;
        if (m == null) {
            throw new IllegalStateException("OpenObjectMapper not initialized");
        }
        return m;
    }

    private static ObjectWriter prettyWriter() {
        ObjectWriter writer = prettyWriter;
        if (writer == null) {
            throw new IllegalStateException("OpenObjectMapper not initialized");
        }
        return writer;
    }

    /**
     * 物件 → JSON 字串。
     */
    public static String toJson(Object value) {
        if (value == null) return null;
        try {
            return mapper().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to serialize object to JSON", ex);
        }
    }

    /**
     * 物件 → 格式化 JSON 字串。
     */
    public static String toPrettyJson(Object value) {
        if (value == null) return null;
        try {
            return prettyWriter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to serialize object to pretty JSON", ex);
        }
    }

    /**
     * 物件 → JSON bytes (UTF-8)。
     */
    public static byte[] toBytes(Object value) {
        if (value == null) return null;
        try {
            return mapper().writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to serialize object to JSON bytes", ex);
        }
    }

    /**
     * JSON → 物件。
     */
    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null) return null;
        try {
            return mapper().readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to deserialize JSON", ex);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null) return null;
        try {
            return mapper().readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to deserialize JSON", ex);
        }
    }

    /**
     * JSON bytes → 物件。
     */
    public static <T> T fromBytes(byte[] jsonBytes, Class<T> type) {
        if (jsonBytes == null) return null;
        try {
            return mapper().readValue(jsonBytes, type);
        } catch (IOException ex) {
            throw new JsonCodecException("Failed to deserialize JSON bytes", ex);
        }
    }

    /**
     * JSON bytes → 泛型物件。
     */
    public static <T> T fromBytes(byte[] jsonBytes, TypeReference<T> type) {
        if (jsonBytes == null) return null;
        try {
            return mapper().readValue(jsonBytes, type);
        } catch (IOException ex) {
            throw new JsonCodecException("Failed to deserialize JSON bytes", ex);
        }
    }

    /**
     * 讀取為樹節點 JsonNode。
     */
    public static JsonNode readTree(String json) {
        if (json == null) return null;
        try {
            return mapper().readTree(json);
        } catch (JsonProcessingException ex) {
            throw new JsonCodecException("Failed to read JSON tree", ex);
        }
    }

    /**
     * 物件 → JsonNode。
     */
    public static JsonNode toNode(Object value) {
        if (value == null) return null;
        return mapper().valueToTree(value);
    }

    /*
     * 物件轉換：DTO ↔ VO ↔ Map，使用具體類別做目標型別。
     * 例如：
     *   UserResponse resp = OpenObjectMapper.convert(user, UserResponse.class);
     *   String[] names = OpenObjectMapper.convert(jsonNode, String[].class);
     *   Integer count = OpenObjectMapper.convert(jsonNode, Integer.class);
     */
    public static <T> T convert(Object src, Class<T> targetType) {
        if (src == null) return null;
        return mapper().convertValue(src, targetType);
    }

    /*
     * 類型轉換：支援泛型目標，避免型別擦除需傳入 TypeReference。
     * 例如：
     *   List<UserResponse> list = OpenObjectMapper.convert(jsonNode, new TypeReference<List<UserResponse>>() {});
     *   Map<String, Object> payload = OpenObjectMapper.convert(jsonNode, new TypeReference<Map<String, Object>>() {});
     */
    public static <T> T convert(Object src, TypeReference<T> targetType) {
        if (src == null) return null;
        return mapper().convertValue(src, targetType);
    }

    /**
     * 清單轉換：來源 null 或空時回傳空集合。
     */
    public static <S, T> List<T> convertList(List<S> source, Class<T> targetType) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .map(item -> convert(item, targetType))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 合併 JSON 到既有物件（部分更新）。
     */
    public static <T> T update(T target, String patchJson) {
        if (target == null || patchJson == null) return target;
        try {
            ObjectReader updatingReader = mapper().readerForUpdating(target);
            return updatingReader.readValue(patchJson);
        } catch (IOException ex) {
            throw new JsonCodecException("Failed to update target with JSON", ex);
        }
    }

    /**
     * 粗略校驗字串是否為 JSON。
     */
    public static boolean isJson(String text) {
        if (text == null) return false;
        try {
            mapper().readTree(text);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * UTF-8 bytes → String，便於手動處理。
     */
    public static String utf8(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }
}
