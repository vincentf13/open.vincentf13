package open.vincentf13.exchange.account.ledger.sdk.rest.api.enums;

public enum OwnerType {
    USER,
    PLATFORM;
    
    public String code() {
        return name();
    }
}
