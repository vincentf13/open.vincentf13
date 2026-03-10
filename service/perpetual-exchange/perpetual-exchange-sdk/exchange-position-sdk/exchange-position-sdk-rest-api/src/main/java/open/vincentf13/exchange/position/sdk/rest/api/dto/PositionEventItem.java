package open.vincentf13.exchange.position.sdk.rest.api.dto;

import java.time.Instant;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;

public record PositionEventItem(
    Long eventId,
    Long positionId,
    Long userId,
    Long instrumentId,
    PositionEventType eventType,
    Long sequenceNumber,
    String payload,
    PositionReferenceType referenceType,
    String referenceId,
    Instant occurredAt,
    Instant createdAt) {}
