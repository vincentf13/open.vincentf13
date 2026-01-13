package open.vincentf13.exchange.market.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/** Market 事件枚舉。 */
public enum MarketEvent implements OpenEvent {
  STARTUP_LOADING_INSTRUMENTS(
      "MarketStartupLoadingInstruments", "Loading instruments from Admin service"),
  STARTUP_INSTRUMENTS_LOADED("MarketStartupInstrumentsLoaded", "Instruments loaded successfully"),
  MARK_PRICE_CACHE_UPDATED("MarkPriceCacheUpdated", "Mark price updated in cache"),
  MARK_PRICE_OUTBOX_APPENDED("MarkPriceOutboxAppended", "MarkPriceUpdated appended to outbox"),
  ORDERBOOK_APPLY_FAILED("OrderBookApplyFailed", "Failed to apply OrderBookUpdated event"),
  ORDERBOOK_PAYLOAD_INVALID("OrderBookPayloadInvalid", "Invalid OrderBookUpdated payload"),
  TRADE_EVENT_PAYLOAD_INVALID("TradeEventPayloadInvalid", "Invalid TradeExecuted payload"),
  KLINE_CLEANUP_TRIGGERED("KlineCleanupTriggered", "Kline bucket cleanup triggered");

  private final String event;
  private final String message;

  MarketEvent(String event, String message) {
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
