package open.vincentf13.exchange.position.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * Position 事件枚舉。
 */
public enum PositionEvent implements OpenEvent {
    POSITION_TRADE_SETTLEMENT_FAILED("PositionTradeSettlementFailed", "Trade settlement handling failed"),
    POSITION_TRADE_PAYLOAD_INVALID("PositionTradePayloadInvalid", "Invalid TradeExecuted payload"),
    STARTUP_CACHE_LOADING("StartupCacheLoading", "Starting cache loading"),
    STARTUP_CACHE_LOADED("StartupCacheLoaded", "Cache loaded successfully"),
    STARTUP_CACHE_LOAD_FAILED("StartupCacheLoadFailed", "Cache loading failed"),
    STARTUP_LOADING_INSTRUMENTS("StartupLoadingInstruments", "Loading instruments from Admin service"),
    STARTUP_INSTRUMENTS_LOADED("StartupInstrumentsLoaded", "Instruments loaded successfully"),
    STARTUP_LOADING_RISK_LIMITS("StartupLoadingRiskLimits", "Loading risk limits from Risk service"),
    STARTUP_RISK_LIMITS_LOADED("StartupRiskLimitsLoaded", "Risk limits loaded successfully"),
    STARTUP_RISK_LIMIT_FETCH_FAILED("StartupRiskLimitFetchFailed", "Failed to fetch risk limit for instrument"),
    STARTUP_RISK_LIMIT_LOAD_PARTIAL("StartupRiskLimitLoadPartial", "Partial risk limit loading"),
    STARTUP_CACHE_LOAD_PARTIAL("StartupCacheLoadPartial", "Partial cache loading during startup");
    
    private final String event;
    private final String message;
    
    PositionEvent(String event,
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
