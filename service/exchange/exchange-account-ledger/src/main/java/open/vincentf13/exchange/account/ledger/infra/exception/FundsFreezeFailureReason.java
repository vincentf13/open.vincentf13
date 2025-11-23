package open.vincentf13.exchange.account.ledger.infra.exception;

public enum FundsFreezeFailureReason {
    INSUFFICIENT_FUNDS,
    DUPLICATE_REQUEST,
    INVALID_AMOUNT,
    INVALID_EVENT
}
