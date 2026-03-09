package open.vincentf13.exchange.common.sdk.enums;

public enum Direction {
  CREDIT,
  DEBIT;

  public String code() {
    return name();
  }
}
