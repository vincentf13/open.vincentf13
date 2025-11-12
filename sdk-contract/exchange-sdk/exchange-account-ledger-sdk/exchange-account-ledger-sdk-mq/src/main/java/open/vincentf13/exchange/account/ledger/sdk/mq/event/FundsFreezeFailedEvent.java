package open.vincentf13.exchange.account.ledger.sdk.mq.event;

public record FundsFreezeFailedEvent(
        Long orderId,
        String reason
) {
}
