package open.vincentf13.exchange.common.sdk.enums;

public enum PlatformAccountStatus {
  ACTIVE,
  INACTIVE;

  public String code() {
    return name();
  }
}
