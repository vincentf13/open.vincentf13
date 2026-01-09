package open.vincentf13.exchange.order.sdk.rest.dto;

import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventReferenceType;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventType;

import java.time.Instant;

public record OrderEventItem(
        Long eventId,
        Long orderId,
        Long userId,
        Long instrumentId,
        OrderEventType eventType,
        Long sequenceNumber,
        String payload,
        OrderEventReferenceType referenceType,
        Long referenceId,
        String actor,
        Instant occurredAt,
        Instant createdAt
) {
}
