package open.vincentf13.exchange.account.ledger.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * Account-Ledger 事件枚舉。
 */
public enum LedgerEvent implements OpenEvent {
    FUNDS_FREEZE_FAILED("FundsFreezeFailed", "Funds freeze failed"),
    INVALID_MARGIN_PRECHECK_EVENT("InvalidMarginPreCheckEvent", "Invalid MarginPreCheckPassedEvent"),
    MATCHING_TRADE_PAYLOAD_MISSING("MatchingTradePayloadMissing", "TradeExecuted payload missing"),
    RISK_MARGIN_IDENTIFIERS_MISSING("RiskMarginIdentifiersMissing", "MarginPreCheckPassed identifiers missing"),
    FUNDS_FROZEN_ENQUEUED("FundsFrozenEnqueued", "FundsFrozen event enqueued"),
    FUNDS_FREEZE_FAILED_ENQUEUED("FundsFreezeFailedEnqueued", "FundsFreezeFailed event enqueued"),
    LEDGER_ENTRY_CREATED_ENQUEUED("LedgerEntryCreatedEnqueued", "LedgerEntryCreated event enqueued");

    private final String event;
    private final String message;

    LedgerEvent(String event, String message) {
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
