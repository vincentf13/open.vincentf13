package open.vincentf13.exchange.position.domain.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionEvent {
    private Long eventId;

    @NotNull
    private Long positionId;

    @NotNull
    private Long userId;

    @NotNull
    private Long instrumentId;

    @NotNull
    private PositionEventType eventType;

    private Long sequenceNumber;

    @NotNull
    private String payload;

    private String referenceId;
    @NotNull
    private PositionReferenceType referenceType;

    @NotNull
    private Instant occurredAt;
    
    private Instant createdAt;

    public static PositionEvent createTradeEvent(Long positionId,
                                                  Long userId,
                                                  Long instrumentId,
                                                  PositionEventType eventType,
                                                  String payload,
                                                  OrderSide side,
                                                  Long tradeId,
                                                  Instant occurredAt,
                                                  boolean isRecursive) {
        String baseId = tradeId + ":" + side.name();
        if (isRecursive) {
            baseId += ":FLIP";
        }
        return PositionEvent.builder()
                .positionId(positionId)
                .userId(userId)
                .instrumentId(instrumentId)
                .eventType(eventType)
                .sequenceNumber(null)
                .payload(payload == null ? "{}" : payload)
                .referenceId(baseId)
                .referenceType(PositionReferenceType.TRADE)
                .occurredAt(occurredAt)
                .build();
    }
}
