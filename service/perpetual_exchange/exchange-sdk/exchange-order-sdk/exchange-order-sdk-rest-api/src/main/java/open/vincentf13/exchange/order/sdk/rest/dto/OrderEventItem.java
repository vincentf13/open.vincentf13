package open.vincentf13.exchange.order.sdk.rest.dto;

import java.time.Instant;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventReferenceType;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventType;

public record OrderEventItem(
    Long eventId,
    Long orderId,
    Long userId,
    Long instrumentId,
    OrderEventType eventType,
    Long sequenceNumber,
    String payload,
    OrderEventReferenceType referenceType,
    String referenceId,
    String actor,
    Instant occurredAt,
    Instant createdAt) {}
