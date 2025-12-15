package open.vincentf13.exchange.matching.infra;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.sdk.core.log.OpenEvent;

@Getter
@RequiredArgsConstructor
public enum MatchingEvent implements OpenEvent {
    WAL_LOADER_FAILED("WAL_LOADER_FAILED", "WAL loader failed"),
    TRADE_DUPLICATE("TRADE_DUPLICATE", "Trade duplicate during batch insert"),
    WAL_ENTRY_APPLIED("WAL_ENTRY_APPLIED", "WAL entry applied to DB"),
    OUTBOX_DUPLICATE_TRADE("OUTBOX_DUPLICATE_TRADE", "Outbox duplicate trade event"),
    OUTBOX_DUPLICATE_ORDERBOOK("OUTBOX_DUPLICATE_ORDERBOOK", "Outbox duplicate order book event");
    
    private final String event;
    private final String message;
    
    @Override
    public String event() {
        return event;
    }
    
    @Override
    public String message() {
        return message;
    }
}
