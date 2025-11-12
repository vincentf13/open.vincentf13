package open.vincentf13.exchange.order.infra.messaging.topic;

public interface LedgerTopics {
    String FUNDS_FROZEN = "ledger.funds-frozen";
    String FUNDS_FREEZE_FAILED = "ledger.funds-freeze-failed";
}
