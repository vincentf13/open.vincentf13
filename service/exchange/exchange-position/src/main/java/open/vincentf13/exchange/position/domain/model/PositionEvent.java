package open.vincentf13.exchange.position.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionEvent {
    @NotNull
    private Long eventId;

    @NotNull
    private Long positionId;

    @NotNull
    private Long userId;

    @NotNull
    private Long instrumentId;

    @NotNull
    private PositionEventType eventType;

    @NotNull
    private BigDecimal deltaQuantity;

    @NotNull
    private BigDecimal deltaPnl;

    @NotNull
    @DecimalMin(ValidationConstant.Names.NON_NEGATIVE)
    private BigDecimal newQuantity;

    @NotNull
    @DecimalMin(ValidationConstant.Names.NON_NEGATIVE)
    private BigDecimal newReservedQuantity;

    @NotNull
    @Positive
    private BigDecimal newEntryPrice;

    @NotNull
    private Integer newLeverage;

    @NotNull
    private BigDecimal newMargin;

    @NotNull
    private BigDecimal newUnrealizedPnl;

    private Long referenceId;
    @NotNull
    private PositionReferenceType referenceType;

    private String metadata; // JSON string

    @NotNull
    private Instant occurredAt;
    
    private Instant createdAt;

    public static PositionEvent createTradeEvent(Long positionId,
                                                  Long userId,
                                                  Long instrumentId,
                                                  PositionEventType eventType,
                                                  BigDecimal deltaQuantity,
                                                  BigDecimal newQuantity,
                                                  BigDecimal newReservedQuantity,
                                                  BigDecimal newEntryPrice,
                                                  Integer newLeverage,
                                                  BigDecimal newMargin,
                                                  BigDecimal newUnrealizedPnl,
                                                  Long tradeId,
                                                  Instant occurredAt) {
        return PositionEvent.builder()
                .positionId(positionId)
                .userId(userId)
                .instrumentId(instrumentId)
                .eventType(eventType)
                .deltaQuantity(deltaQuantity)
                .deltaPnl(BigDecimal.ZERO)
                .newQuantity(newQuantity)
                .newReservedQuantity(newReservedQuantity)
                .newEntryPrice(newEntryPrice)
                .newLeverage(newLeverage)
                .newMargin(newMargin)
                .newUnrealizedPnl(newUnrealizedPnl)
                .referenceId(tradeId)
                .referenceType(PositionReferenceType.TRADE)
                .metadata("")
                .occurredAt(occurredAt)
                .build();
    }
}
