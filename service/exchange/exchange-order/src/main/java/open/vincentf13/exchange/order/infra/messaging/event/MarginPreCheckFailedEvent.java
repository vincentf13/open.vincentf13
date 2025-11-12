package open.vincentf13.exchange.order.infra.messaging.event;

/**
 * Emitted by risk service when pre-check fails before funds freezing.
 */
public record MarginPreCheckFailedEvent(
        Long orderId,
        String reason
) {
}
