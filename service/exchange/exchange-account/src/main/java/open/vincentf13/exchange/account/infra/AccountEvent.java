package open.vincentf13.exchange.account.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/**
 Account 事件枚舉。
 */
public enum AccountEvent implements OpenEvent {
    MATCHING_TRADE_PAYLOAD_MISSING("MatchingTradePayloadMissing", "TradeExecuted payload missing"),
    ORDER_SUBMITTED_PAYLOAD_INVALID("OrderSubmittedPayloadInvalid", "OrderSubmitted payload invalid");
    
    private final String event;
    private final String message;
    
    AccountEvent(String event,
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
