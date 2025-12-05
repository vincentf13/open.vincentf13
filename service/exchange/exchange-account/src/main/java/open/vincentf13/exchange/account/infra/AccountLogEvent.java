package open.vincentf13.exchange.account.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

public enum AccountLogEvent implements OpenEvent {

    STARTUP_CACHE_LOADING("AccountStartupCacheLoading", "Starting cache loading"),
    STARTUP_CACHE_LOADED("AccountStartupCacheLoaded", "Cache loaded successfully"),
    STARTUP_CACHE_LOAD_FAILED("AccountStartupCacheLoadFailed", "Cache loading failed"),
    STARTUP_LOADING_INSTRUMENTS("AccountStartupLoadingInstruments", "Loading instruments from Admin service"),
    STARTUP_INSTRUMENTS_LOADED("AccountStartupInstrumentsLoaded", "Instruments loaded successfully"),
    INSTRUMENT_FETCH_FAILED("AccountInstrumentFetchFailed", "Instrument fetch failed");

   
    private final String event;
    private final String message;
    
    AccountLogEvent(String event, String message) {
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
