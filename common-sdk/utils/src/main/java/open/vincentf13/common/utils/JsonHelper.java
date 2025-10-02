package open.vincentf13.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Simple JSON helper exposed via common utilities.
 */
public final class JsonHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonHelper() {
    }

    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize object to JSON", ex);
        }
    }
}
