package open.vincentf13.exchange.risk.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

public enum RiskLogEvent implements OpenEvent {
    STARTUP_CACHE_LOADING("Startup cache loading"),
    STARTUP_CACHE_LOADED("Startup cache loaded"),
    STARTUP_CACHE_LOAD_FAILED("Startup cache load failed"),
    STARTUP_LOADING_INSTRUMENTS("Startup loading instruments"),
    STARTUP_INSTRUMENTS_LOADED("Startup instruments loaded");

    private final String message;

    RiskLogEvent(String message) {
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
