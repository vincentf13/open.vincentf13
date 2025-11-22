package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

public enum Direction {
    CREDIT,
    DEBIT;

    public String code() {
        return name();
    }
}
