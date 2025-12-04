package open.vincentf13.exchange.account.ledger.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/**
 Account-Ledger 事件枚舉。
 */
public enum LedgerEvent implements OpenEvent {
    MATCHING_TRADE_PAYLOAD_MISSING("MatchingTradePayloadMissing", "TradeExecuted payload missing"),
    LEDGER_ENTRY_CREATED_ENQUEUED("LedgerEntryCreatedEnqueued", "LedgerEntryCreated event enqueued");
    
    private final String event;
    private final String message;
    
    LedgerEvent(String event,
                String message) {
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
