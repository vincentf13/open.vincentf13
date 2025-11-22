package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

public enum EntryType {
    DEPOSIT,
    WITHDRAWAL;

    public String code() {
        return name();
    }
}
