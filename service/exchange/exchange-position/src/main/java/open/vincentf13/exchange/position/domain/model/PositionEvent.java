package open.vincentf13.exchange.position.domain.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionEventType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionReferenceType;

import java.math.BigDecimal;
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

    @NotNull
    private BigDecimal deltaQuantity;

    @NotNull
    private BigDecimal deltaMargin;

    @NotNull
    private BigDecimal realizedPnl;

    @NotNull
    private BigDecimal tradeFee;

    @NotNull
    private BigDecimal fundingFee;

    @NotNull
    private BigDecimal newQuantity;

    @NotNull
    private BigDecimal newReservedQuantity;

    @NotNull
    private BigDecimal newEntryPrice;

    @NotNull
    private Integer newLeverage;

    @NotNull
    private BigDecimal newMargin;

    @NotNull
    private BigDecimal newUnrealizedPnl;

    @NotNull
    private BigDecimal newLiquidationPrice;

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
                                                  BigDecimal deltaMargin,
                                                  BigDecimal realizedPnl,
                                                  BigDecimal tradeFee,
                                                  BigDecimal fundingFee,
                                                  BigDecimal newQuantity,
                                                  BigDecimal newReservedQuantity,
                                                  BigDecimal newEntryPrice,
                                                  Integer newLeverage,
                                                  BigDecimal newMargin,
                                                  BigDecimal newUnrealizedPnl,
                                                  BigDecimal newLiquidationPrice,
                                                  Long tradeId,
                                                  Instant occurredAt) {
        return PositionEvent.builder()
                .positionId(positionId)
                .userId(userId)
                .instrumentId(instrumentId)
                .eventType(eventType)
                .deltaQuantity(deltaQuantity)
                .deltaMargin(deltaMargin)
                .realizedPnl(realizedPnl)
                .tradeFee(tradeFee)
                .fundingFee(fundingFee)
                .newQuantity(newQuantity)
                .newReservedQuantity(newReservedQuantity)
                .newEntryPrice(newEntryPrice)
                .newLeverage(newLeverage)
                .newMargin(newMargin)
                .newUnrealizedPnl(newUnrealizedPnl)
                .newLiquidationPrice(newLiquidationPrice)
                .referenceId(tradeId)
                .referenceType(PositionReferenceType.TRADE)
                .metadata("")
                .occurredAt(occurredAt)
                .build();
    }
}
