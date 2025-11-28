package open.vincentf13.exchange.account.ledger.sdk.rest.api.enums;

public enum PlatformAccountCategory {
    ASSET,
    LIABILITY,
    REVENUE,
    EXPENSE,
    EQUITY;
    
    public String code() {
        return name();
    }
}
