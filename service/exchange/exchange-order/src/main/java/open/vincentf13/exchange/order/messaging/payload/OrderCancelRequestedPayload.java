package open.vincentf13.exchange.order.messaging.payload;

import java.time.Instant;

public record OrderCancelRequestedPayload(
        Long orderId,
        Instant requestedAt,
        String reason
) {
}
