package open.vincentf13.sdk.core.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * OpenObjectDiff: Object difference utility.
 */
public final class OpenObjectDiff {

    private OpenObjectDiff() {
    }

    /**
     * Compares two objects and returns a JSON string representing the fields that have changed in 'after'.
     *
     * @param before The baseline object (can be null).
     * @param after  The updated object (can be null).
     * @param <T>    The type of the objects.
     * @return A JSON string of differences, or "{}" if no differences.
     */
    public static <T> String diff(T before, T after) {
        if (before == after) {
            return "{}";
        }
        
        JsonNode beforeNode = before == null ? JsonNodeFactory.instance.objectNode() : OpenObjectMapper.toNode(before);
        JsonNode afterNode = after == null ? JsonNodeFactory.instance.objectNode() : OpenObjectMapper.toNode(after);

        if (beforeNode == null) beforeNode = JsonNodeFactory.instance.objectNode();
        if (afterNode == null) afterNode = JsonNodeFactory.instance.objectNode();

        if (!beforeNode.isObject() || !afterNode.isObject()) {
            return OpenObjectMapper.toJson(after);
        }

        ObjectNode diff = JsonNodeFactory.instance.objectNode();
        Set<String> allKeys = new HashSet<>();
        beforeNode.fieldNames().forEachRemaining(allKeys::add);
        afterNode.fieldNames().forEachRemaining(allKeys::add);

        for (String key : allKeys) {
            JsonNode beforeVal = beforeNode.get(key);
            JsonNode afterVal = afterNode.get(key);

            if (!isSame(beforeVal, afterVal)) {
                if (afterVal == null || afterVal.isNull()) {
                    diff.putNull(key);
                } else {
                    diff.set(key, afterVal);
                }
            }
        }
        
        return diff.toString();
    }

    private static boolean isSame(JsonNode v1, JsonNode v2) {
        if (v1 == v2) return true;
        if (v1 == null && v2 == null) return true;
        if (v1 == null || v2 == null) return false; // One is missing/null, other is not.

        // NullNode check
        if (v1.isNull() && v2.isNull()) return true;
        if (v1.isNull() != v2.isNull()) return false;

        // Number special handling (BigDecimal scale)
        if (v1.isNumber() && v2.isNumber()) {
            if (v1.isBigDecimal() || v2.isBigDecimal() || v1.isFloatingPointNumber() || v2.isFloatingPointNumber()) {
                BigDecimal b1 = v1.decimalValue();
                BigDecimal b2 = v2.decimalValue();
                return b1.compareTo(b2) == 0;
            }
        }

        return v1.equals(v2);
    }
}
