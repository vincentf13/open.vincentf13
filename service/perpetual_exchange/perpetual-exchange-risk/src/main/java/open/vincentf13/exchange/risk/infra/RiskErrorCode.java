package open.vincentf13.exchange.risk.infra;

import open.vincentf13.sdk.core.exception.OpenErrorCode;

public enum RiskErrorCode implements OpenErrorCode {
  RISK_LIMIT_NOT_FOUND("Risk-404-1001", "Risk limit not found");

  private final String code;
  private final String message;

  RiskErrorCode(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String message() {
    return message;
  }
}
