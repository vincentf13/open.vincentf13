package open.vincentf13.exchange.position.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

/**
  Position 事件枚舉。
 */
public enum PositionEvent implements OpenEvent {
    POSITION_TRADE_SETTLEMENT_FAILED("PositionTradeSettlementFailed", "Trade settlement handling failed"),
    POSITION_TRADE_PAYLOAD_INVALID("PositionTradePayloadInvalid", "Invalid TradeExecuted payload"),
    POSITION_MARK_PRICE_UPDATE_FAILED("PositionMarkPriceUpdateFailed", "Mark price update failed"),
    STARTUP_LOADING_INSTRUMENTS("PositionStartupLoadingInstruments", "Loading instruments from Admin service"),
    STARTUP_INSTRUMENTS_LOADED("PositionStartupInstrumentsLoaded", "Instruments loaded successfully"),
    STARTUP_LOADING_RISK_LIMITS("PositionStartupLoadingRiskLimits", "Loading risk limits from Risk service"),
    STARTUP_RISK_LIMITS_LOADED("PositionStartupRiskLimitsLoaded", "Risk limits loaded successfully"),
    STARTUP_RISK_LIMIT_FETCH_FAILED("PositionStartupRiskLimitFetchFailed", "Failed to fetch risk limit for instrument"),
    STARTUP_RISK_LIMIT_LOAD_PARTIAL("PositionStartupRiskLimitLoadPartial", "Partial risk limit loading"),
    STARTUP_CACHE_LOAD_PARTIAL("PositionStartupCacheLoadPartial", "Partial cache loading during startup");
    
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
