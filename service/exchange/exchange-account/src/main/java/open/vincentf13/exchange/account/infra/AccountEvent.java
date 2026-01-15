package open.vincentf13.exchange.account.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/** Account 事件枚舉。 */
public enum AccountEvent implements OpenEvent {
  STARTUP_LOADING_INSTRUMENTS(
      "AccountStartupLoadingInstruments", "Loading instruments from Admin service"),
  STARTUP_INSTRUMENTS_LOADED("AccountStartupInstrumentsLoaded", "Instruments loaded successfully"),
  STARTUP_LOADING_RISK_LIMITS(
      "AccountStartupLoadingRiskLimits", "Loading risk limits from Risk service"),
  STARTUP_RISK_LIMITS_LOADED("AccountStartupRiskLimitsLoaded", "Risk limits loaded successfully"),
  INSTRUMENT_FETCH_FAILED("AccountInstrumentFetchFailed", "Instrument fetch failed"),
  MATCHING_TRADE_PAYLOAD_MISSING("MatchingTradePayloadMissing", "TradeExecuted payload missing"),
  FUNDS_FREEZE_REQUEST_PAYLOAD_INVALID(
      "FundsFreezeRequestPayloadInvalid", "Funds freeze request payload invalid"),
  POSITION_CLOSE_TO_OPEN_COMPENSATION_PAYLOAD_INVALID(
      "PositionCloseToOpenCompensationPayloadInvalid",
      "Position close to open compensation payload invalid"),
  POSITION_OPEN_TO_CLOSE_COMPENSATION_PAYLOAD_INVALID(
      "PositionOpenToCloseCompensationPayloadInvalid",
      "Position open to close compensation payload invalid"),
  INVALID_FILL_NEGATIVE_BALANCE(
      "InvalidFillNegativeBalance", "Invalid fill resulted in negative balance");

  private final String event;
  private final String message;

  AccountEvent(String event, String message) {
    this.event = event;
    this.message = message;
  }

  @Override
  public String event() {
    return event;
  }

  @Override
  public String message() {
    return message;
  }
}
