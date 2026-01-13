package open.vincentf13.sdk.core.values;

public final class OpenString {

  private OpenString() {}

  /**
   在原字串結尾補上句號。

   當內容不為空且結尾不是 "." 或 "。" 時，會補上 "。"；其他情況直接回傳原字串。
   */
  public static String ensureEndsWithPeriod(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return value;
    }
    if (trimmed.endsWith(".") || trimmed.endsWith("。")) {
      return trimmed;
    }
    return trimmed + "。";
  }
}
