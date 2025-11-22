package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

public enum PlatformAccountStatus {
    ACTIVE,
    INACTIVE;

    public String code() {
        return name();
    }
}
