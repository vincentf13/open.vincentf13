package open.vincentf13.exchange.order.mq.event;

import java.time.Instant;

public record OrderCancelRequestedEvent(
        Long orderId,
        Long userId,
        Instant requestedAt,
        String reason
) {
}
