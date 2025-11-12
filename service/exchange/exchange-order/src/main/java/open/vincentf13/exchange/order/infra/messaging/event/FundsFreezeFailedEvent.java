package open.vincentf13.exchange.order.infra.messaging.event;

/**
 * Emitted by ledger when funds cannot be frozen for an order.
 */
public record FundsFreezeFailedEvent(
        Long orderId,
        String reason
) {
}
