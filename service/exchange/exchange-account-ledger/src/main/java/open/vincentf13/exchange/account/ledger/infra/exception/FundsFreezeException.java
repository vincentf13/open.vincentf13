package open.vincentf13.exchange.account.ledger.infra.exception;

public class FundsFreezeException extends RuntimeException {

    private final FundsFreezeFailureReason reason;

    public FundsFreezeException(FundsFreezeFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public FundsFreezeFailureReason getReason() {
        return reason;
    }
}
