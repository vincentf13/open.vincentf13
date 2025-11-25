package open.vincentf13.exchange.marketdata.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * Market Data 事件枚舉。
 */
public enum MarketDataEventEnum implements OpenEvent {
    MARK_PRICE_CACHE_UPDATED("MarkPriceCacheUpdated", "Mark price updated in cache"),
    MARK_PRICE_OUTBOX_APPENDED("MarkPriceOutboxAppended", "MarkPriceUpdated appended to outbox"),
    ORDERBOOK_APPLY_FAILED("OrderBookApplyFailed", "Failed to apply OrderBookUpdated event"),
    KLINE_CLEANUP_TRIGGERED("KlineCleanupTriggered", "Kline bucket cleanup triggered");

    private final String event;
    private final String message;

    MarketDataEventEnum(String event, String message) {
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
