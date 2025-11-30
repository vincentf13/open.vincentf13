package open.vincentf13.exchange.position.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/*
 * Position 事件枚舉。
 */
public enum PositionLogEvent implements OpenEvent {
    POSITION_RESERVED("PositionReserved", "Position reserved"),
    POSITION_RESERVE_REJECTED("PositionReserveRejected", "Position reserve rejected"),
    POSITION_LEVERAGE_UNCHANGED("PositionLeverageUnchanged", "Leverage unchanged"),
    POSITION_LEVERAGE_UPDATED("PositionLeverageUpdated", "Position leverage updated"),
    POSITION_RESERVE_PAYLOAD_INVALID("PositionReservePayloadInvalid", "Invalid PositionReserveRequested payload"),
    POSITION_TRADE_PAYLOAD_INVALID("PositionTradePayloadInvalid", "Invalid TradeExecuted payload"),
    STARTUP_CACHE_LOADING("StartupCacheLoading", "Starting cache loading"),
    STARTUP_CACHE_LOADED("StartupCacheLoaded", "Cache loaded successfully"),
    STARTUP_CACHE_LOAD_FAILED("StartupCacheLoadFailed", "Cache loading failed"),
    STARTUP_LOADING_INSTRUMENTS("StartupLoadingInstruments", "Loading instruments from Admin service"),
    STARTUP_INSTRUMENTS_LOADED("StartupInstrumentsLoaded", "Instruments loaded successfully"),
    STARTUP_LOADING_RISK_LIMITS("StartupLoadingRiskLimits", "Loading risk limits from Risk service"),
    STARTUP_RISK_LIMITS_LOADED("StartupRiskLimitsLoaded", "Risk limits loaded successfully"),
    STARTUP_RISK_LIMIT_FETCH_FAILED("StartupRiskLimitFetchFailed", "Failed to fetch risk limit for instrument"),
    STARTUP_RISK_LIMIT_LOAD_PARTIAL("StartupRiskLimitLoadPartial", "Partial risk limit loading");
    
    private final String event;
    private final String message;
    
    PositionLogEvent(String event,
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
