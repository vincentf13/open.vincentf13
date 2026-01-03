package open.vincentf13.exchange.account.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/**
  Account 事件枚舉。
 */
public enum AccountEvent implements OpenEvent {
    STARTUP_CACHE_LOADING("AccountStartupCacheLoading", "Starting cache loading"),
    STARTUP_CACHE_LOADED("AccountStartupCacheLoaded", "Cache loaded successfully"),
    STARTUP_CACHE_LOAD_FAILED("AccountStartupCacheLoadFailed", "Cache loading failed"),
    STARTUP_LOADING_INSTRUMENTS("AccountStartupLoadingInstruments", "Loading instruments from Admin service"),
    STARTUP_INSTRUMENTS_LOADED("AccountStartupInstrumentsLoaded", "Instruments loaded successfully"),
    INSTRUMENT_FETCH_FAILED("AccountInstrumentFetchFailed", "Instrument fetch failed"),
    MATCHING_TRADE_PAYLOAD_MISSING("MatchingTradePayloadMissing", "TradeExecuted payload missing"),
    FUNDS_FREEZE_REQUEST_PAYLOAD_INVALID("FundsFreezeRequestPayloadInvalid", "Funds freeze request payload invalid"),
    POSITION_CLOSE_TO_OPEN_COMPENSATION_PAYLOAD_INVALID("PositionCloseToOpenCompensationPayloadInvalid", "Close-to-open compensation payload invalid"),
    INSUFFICIENT_RESERVED_BALANCE("InsufficientReservedBalance", "Reserved balance insufficient for settlement"),
    INVALID_FILL_NEGATIVE_BALANCE("InvalidFillNegativeBalance", "Invalid fill settlement led to negative balance");
    
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
