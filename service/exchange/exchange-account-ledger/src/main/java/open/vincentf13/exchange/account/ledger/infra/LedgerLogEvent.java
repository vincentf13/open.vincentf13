package open.vincentf13.exchange.account.ledger.infra;

import open.vincentf13.sdk.core.log.OpenEvent;

public enum LedgerLogEvent implements OpenEvent {

    STARTUP_CACHE_LOADING("LedgerStartupCacheLoading", "Starting cache loading"),
    STARTUP_CACHE_LOADED("LedgerStartupCacheLoaded", "Cache loaded successfully"),
    STARTUP_CACHE_LOAD_FAILED("LedgerStartupCacheLoadFailed", "Cache loading failed"),
    STARTUP_LOADING_INSTRUMENTS("LedgerStartupLoadingInstruments", "Loading instruments from Admin service"),
    STARTUP_INSTRUMENTS_LOADED("LedgerStartupInstrumentsLoaded", "Instruments loaded successfully"),
    INSTRUMENT_FETCH_FAILED("LedgerInstrumentFetchFailed", "Instrument fetch failed");

   
    private final String event;
    private final String message;
    
    LedgerLogEvent(String event, String message) {
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
