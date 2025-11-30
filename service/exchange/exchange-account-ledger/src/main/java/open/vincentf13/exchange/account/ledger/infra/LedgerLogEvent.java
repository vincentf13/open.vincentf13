package open.vincentf13.exchange.account.ledger.infra;

import open.vincentf13.sdk.core.log.OpenLogEvent;

public enum LedgerLogEvent implements OpenLogEvent {

    STARTUP_CACHE_LOADING("AL-0001", "Account Ledger startup cache loading started"),
    STARTUP_CACHE_LOADED("AL-0002", "Account Ledger startup caches loaded successfully"),
    STARTUP_CACHE_LOAD_FAILED("AL-0003", "Account Ledger startup cache loading failed"),
    STARTUP_LOADING_INSTRUMENTS("AL-0004", "Account Ledger loading instruments from Admin service"),
    STARTUP_INSTRUMENTS_LOADED("AL-0005", "Account Ledger instruments loaded"),
    INSTRUMENT_FETCH_FAILED("AL-0006", "Account Ledger instrument fetch failed");

    private final String code;
    private final String description;

    LedgerLogEvent(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
