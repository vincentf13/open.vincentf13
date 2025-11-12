package open.vincentf13.exchange.risk.margin.sdk.mq.event;

public record MarginPreCheckFailedEvent(
        Long orderId,
        String reason
) {
}
