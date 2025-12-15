package open.vincentf13.exchange.risk.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

public enum RiskEvent implements OpenEvent {
    STARTUP_CACHE_LOADING("Startup cache loading"),
    STARTUP_CACHE_LOADED("Startup cache loaded"),
    STARTUP_CACHE_LOAD_FAILED("Startup cache load failed"),
    STARTUP_LOADING_INSTRUMENTS("Startup loading instruments"),
    STARTUP_INSTRUMENTS_LOADED("Startup instruments loaded"),
    STARTUP_MARK_PRICE_LOAD_FAILED("Startup mark price load failed");

    private final String message;

    RiskEvent(String message) {
        this.message = message;
    }

    @Override
    public String event() {
        return name();
    }

    @Override
    public String message() {
        return message;
    }
}
