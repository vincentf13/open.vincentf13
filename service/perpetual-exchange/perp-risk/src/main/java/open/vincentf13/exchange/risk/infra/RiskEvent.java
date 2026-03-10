package open.vincentf13.exchange.risk.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

public enum RiskEvent implements OpenEvent {
  STARTUP_LOADING_INSTRUMENTS(
      "RiskStartupLoadingInstruments", "Loading instruments from Admin service"),
  STARTUP_INSTRUMENTS_LOADED("RiskStartupInstrumentsLoaded", "Instruments loaded successfully"),
  STARTUP_MARK_PRICE_LOAD_FAILED("RiskStartupMarkPriceLoadFailed", "Mark price load failed"),
  STARTUP_MARK_PRICE_LOAD_START("RiskStartupMarkPriceLoadStart", "Loading mark prices from Market service"),
  STARTUP_MARK_PRICE_LOADED("RiskStartupMarkPriceLoaded", "Mark prices loaded successfully");

  private final String event;
  private final String message;

  RiskEvent(String event, String message) {
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
