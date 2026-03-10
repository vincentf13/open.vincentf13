package open.vincentf13.exchange.common.sdk.enums;

public enum OwnerType {
  USER,
  PLATFORM;

  public String code() {
    return name();
  }
}
