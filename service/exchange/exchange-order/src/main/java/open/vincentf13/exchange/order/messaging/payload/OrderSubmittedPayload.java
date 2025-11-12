package open.vincentf13.exchange.order.messaging.payload;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;

import java.time.Instant;

public record OrderSubmittedPayload(
        Long orderId,
        OrderStatus status,
        Instant submittedAt
) {
}
