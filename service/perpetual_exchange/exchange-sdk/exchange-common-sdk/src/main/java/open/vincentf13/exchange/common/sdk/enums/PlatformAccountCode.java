package open.vincentf13.exchange.common.sdk.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PlatformAccountCode {
  USER_DEPOSIT("User Deposit"),
  FEE_REVENUE("Fee Revenue");

  private final String displayName;

  PlatformAccountCode(String displayName) {
    this.displayName = displayName;
  }

  @JsonCreator
  public static PlatformAccountCode fromValue(String value) {
    if (value == null) {
      return null;
    }
    return PlatformAccountCode.valueOf(value);
  }

  @JsonValue
  public String code() {
    return name();
  }

  public String displayName() {
    return displayName;
  }
}
