package open.vincentf13.sdk.core.object;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;

/** OpenObjectDiff: 物件差異比對工具。 */
public final class OpenObjectDiff {

  private OpenObjectDiff() {}

  /**
   * 比對兩個物件並回傳表示 'after' 中已變更欄位的 JSON 字串。
   *
   * @param before 基準物件（可為 null）。
   * @param after 更新後的物件（可為 null）。
   * @param <T> 物件類型。
   * @return 差異的 JSON 字串，若無差異則回傳 "{}"。
   */
  public static <T> String diff(T before, T after) {
    if (before == after) {
      return "{}";
    }

    JsonNode beforeNode =
        before == null ? JsonNodeFactory.instance.objectNode() : OpenObjectMapper.toNode(before);
    JsonNode afterNode =
        after == null ? JsonNodeFactory.instance.objectNode() : OpenObjectMapper.toNode(after);

    if (beforeNode == null) beforeNode = JsonNodeFactory.instance.objectNode();
    if (afterNode == null) afterNode = JsonNodeFactory.instance.objectNode();

    // 處理基本類型或非物件的情況
    if (!beforeNode.isObject() || !afterNode.isObject()) {
      if (isSame(beforeNode, afterNode)) {
        return "{}";
      }
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
        // 如果 afterVal 為 null 或缺失，則在 diff 中明確記錄為 null
        if (afterVal == null || afterVal.isNull()) {
          diff.putNull(key);
        } else {
          diff.set(key, afterVal);
        }
      }
    }

    return diff.toString();
  }

  /**
   * 比對兩個物件並回傳差異 JSON 字串。對於數值類型，回傳差值（after - before）；其他類型回傳 after 的值。 數值類型的 null 將會視為 0
   *
   * @param before 基準物件（可為 null）。
   * @param after 更新後的物件（可為 null）。
   * @param <T> 物件類型。
   * @return 差異的 JSON 字串，若無差異則回傳 "{}"。
   */
  public static <T> String diffDelta(T before, T after) {
    if (before == after) {
      return "{}";
    }

    JsonNode beforeNode =
        before == null ? JsonNodeFactory.instance.objectNode() : OpenObjectMapper.toNode(before);
    JsonNode afterNode =
        after == null ? JsonNodeFactory.instance.objectNode() : OpenObjectMapper.toNode(after);

    if (beforeNode == null) beforeNode = JsonNodeFactory.instance.objectNode();
    if (afterNode == null) afterNode = JsonNodeFactory.instance.objectNode();

    // 處理基本類型或非物件的情況
    if (!beforeNode.isObject() || !afterNode.isObject()) {
      if (isSame(beforeNode, afterNode)) {
        return "{}";
      }
      // 若為數值，回傳差值
      if (isNumberOrNull(beforeNode) && isNumberOrNull(afterNode)) {
        BigDecimal b1 = toBigDecimal(beforeNode);
        BigDecimal b2 = toBigDecimal(afterNode);
        return b2.subtract(b1).toString();
      }
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
        if (isNumberOrNull(beforeVal) && isNumberOrNull(afterVal)) {
          BigDecimal b1 = toBigDecimal(beforeVal);
          BigDecimal b2 = toBigDecimal(afterVal);
          diff.put(key, b2.subtract(b1));
        } else {
          // 如果 afterVal 為 null 或缺失，則在 diff 中明確記錄為 null
          if (afterVal == null || afterVal.isNull()) {
            diff.putNull(key);
          } else {
            diff.set(key, afterVal);
          }
        }
      }
    }

    return diff.toString();
  }

  private static boolean isNumberOrNull(JsonNode node) {
    return node == null || node.isNull() || node.isNumber();
  }

  private static BigDecimal toBigDecimal(JsonNode node) {
    if (node == null || node.isNull()) {
      return BigDecimal.ZERO;
    }
    if (node.isNumber()) {
      return node.decimalValue();
    }
    return BigDecimal.ZERO; // Should not happen if checked by isNumberOrNull, but safe fallback
  }

  /** 判斷兩個 JsonNode 是否邏輯相等（特別處理了 BigDecimal 的數值相等性）。 */
  private static boolean isSame(JsonNode v1, JsonNode v2) {
    if (v1 == v2) return true;
    if (v1 == null && v2 == null) return true;
    if (v1 == null || v2 == null) return false;

    // NullNode 檢查
    if (v1.isNull() && v2.isNull()) return true;
    if (v1.isNull() != v2.isNull()) return false;

    // 數值特殊處理（忽略 BigDecimal 的 scale 差異）
    if (v1.isNumber() && v2.isNumber()) {
      if (v1.isBigDecimal()
          || v2.isBigDecimal()
          || v1.isFloatingPointNumber()
          || v2.isFloatingPointNumber()) {
        BigDecimal b1 = v1.decimalValue();
        BigDecimal b2 = v2.decimalValue();
        return b1.compareTo(b2) == 0;
      }
    }

    return v1.equals(v2);
  }
}
