package open.vincentf13.sdk.core.values;

public final class OpenEnum {

  private OpenEnum() {}

  public static <T extends Enum<T>> T resolve(String value, Class<T> enumType) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
