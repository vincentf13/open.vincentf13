package open.vincentf13.exchange.position.domain.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.sdk.core.object.OpenObjectDiff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    
    private Long positionId;
    @NotNull
    private Long userId;
    @NotNull
    private Long instrumentId;
    @NotNull
    private Integer leverage;
    @NotNull
    private BigDecimal margin;
    @NotNull
    private PositionSide side;
    @NotNull
    private BigDecimal entryPrice;
    @NotNull
    private BigDecimal quantity;
    @NotNull
    private BigDecimal closingReservedQuantity;
    @NotNull
    private BigDecimal markPrice;
    @NotNull
    private BigDecimal marginRatio;
    @NotNull
    private BigDecimal unrealizedPnl;
    private BigDecimal liquidationPrice;
    @NotNull
    private BigDecimal cumRealizedPnl;
    @NotNull
    private BigDecimal cumFee;
    @NotNull
    private BigDecimal cumFundingFee;
    
    @NotNull
    private PositionStatus status;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;
    
    public static Position createDefault(Long userId,
                                         Long instrumentId,
                                         PositionSide side,
                                         Integer defaultLeverage) {
        Integer leverage = defaultLeverage == null || defaultLeverage <= 0 ? 1 : defaultLeverage;
        return Position.builder()
                       .userId(userId)
                       .instrumentId(instrumentId)
                       .leverage(leverage)
                       .margin(BigDecimal.ZERO)
                       .side(side == null ? PositionSide.LONG : side)
                       .entryPrice(BigDecimal.ZERO)
                       .quantity(BigDecimal.ZERO)
                       .closingReservedQuantity(BigDecimal.ZERO)
                       .markPrice(BigDecimal.ZERO)
                       .marginRatio(BigDecimal.ZERO)
                       .unrealizedPnl(BigDecimal.ZERO)
                       .liquidationPrice(null)
                       .cumRealizedPnl(BigDecimal.ZERO)
                       .cumFee(BigDecimal.ZERO)
                       .cumFundingFee(BigDecimal.ZERO)
                       .version(0)
                       .status(PositionStatus.ACTIVE)
                       .build();
    }
    
    public int safeVersion() {
        return version == null ? 0 : version;
    }
    
    public BigDecimal availableToClose() {
        BigDecimal reserved = closingReservedQuantity == null ? BigDecimal.ZERO : closingReservedQuantity;
        BigDecimal currentQuantity = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal available = currentQuantity.subtract(reserved);
        return available.max(BigDecimal.ZERO);
    }
    
    
    public boolean isSameSide(PositionSide otherSide) {
        if (side == null || otherSide == null) {
            return true;
        }
        return side == otherSide;
    }
    
    public boolean shouldSplitTrade(PositionSide targetSide,
                                    BigDecimal quantity) {
        if (targetSide == null || quantity == null) {
            return false;
        }
        return side != targetSide && this.quantity.compareTo(quantity) < 0;
    }
    
    public TradeSplit calculateTradeSplit(BigDecimal quantity) {
        // Flip 操作將優先佔用所有倉位（包括已被其他平倉單預扣的部分）。由於 Flip 訂單在下單時被視為開倉並預扣了資金，但此處實際執行了平倉，因此後續必須發送補償事件以退還重複計算的保證金。
        BigDecimal closeQty = this.quantity;
        BigDecimal flipQty = quantity.subtract(closeQty);
        return new TradeSplit(closeQty, flipQty);
    }
    
    public record TradeSplit(BigDecimal closeQuantity, BigDecimal flipQuantity) {
    }
    
    public void applyOpen(BigDecimal tradePrice,
                          BigDecimal effectiveMarkPrice,
                          BigDecimal quantity,
                          BigDecimal marginDelta,
                          BigDecimal feeCharged,
                          BigDecimal contractMultiplier,
                          BigDecimal maintenanceMarginRate) {
        BigDecimal newQuantity = this.quantity.add(quantity);
        BigDecimal newEntryPrice = this.entryPrice
                                           .multiply(this.quantity)
                                           .add(tradePrice.multiply(quantity))
                                           .divide(newQuantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
        this.entryPrice = newEntryPrice;
        this.quantity = newQuantity;
        this.margin = this.margin.add(marginDelta);
        this.cumRealizedPnl = this.cumRealizedPnl.subtract(feeCharged);
        this.cumFee = this.cumFee.add(feeCharged);
        this.markPrice = effectiveMarkPrice;
        this.status = PositionStatus.ACTIVE;
        this.closedAt = null;
        updateRiskMetrics(effectiveMarkPrice, contractMultiplier, maintenanceMarginRate);
    }
    
    public CloseMetrics applyClose(BigDecimal tradePrice,
                                   BigDecimal effectiveMarkPrice,
                                   BigDecimal quantity,
                                   BigDecimal feeCharged,
                                   boolean isFlip,
                                   Instant executedAt,
                                   BigDecimal contractMultiplier,
                                   BigDecimal maintenanceMarginRate) {
        BigDecimal marginReleased = this.margin
                                            .multiply(quantity)
                                            .divide(this.quantity, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
        BigDecimal pnl = calculateRealizedPnl(this.side,
                                              this.entryPrice,
                                              tradePrice,
                                              quantity,
                                              contractMultiplier);
        BigDecimal newQuantity = this.quantity.subtract(quantity);
        this.quantity = newQuantity;
        this.margin = this.margin.subtract(marginReleased);
        this.cumRealizedPnl = this.cumRealizedPnl.add(pnl).subtract(feeCharged);
        this.cumFee = this.cumFee.add(feeCharged);
        if (isFlip) {
            this.closingReservedQuantity = BigDecimal.ZERO;
        } else {
            this.closingReservedQuantity = this.closingReservedQuantity.subtract(quantity).max(BigDecimal.ZERO);
        }
        this.markPrice = effectiveMarkPrice;
        if (newQuantity.compareTo(BigDecimal.ZERO) == 0) {
            this.status = PositionStatus.CLOSED;
            this.closedAt = executedAt;
        } else {
            this.status = PositionStatus.ACTIVE;
            this.closedAt = null;
        }
        updateRiskMetrics(effectiveMarkPrice, contractMultiplier, maintenanceMarginRate);
        return new CloseMetrics(marginReleased, pnl);
    }
    
    public void applyMarkPrice(BigDecimal markPrice,
                               BigDecimal contractMultiplier,
                               BigDecimal maintenanceMarginRate) {
        this.markPrice = markPrice;
        updateRiskMetrics(markPrice, contractMultiplier, maintenanceMarginRate);
    }
    
    private void updateRiskMetrics(BigDecimal effectiveMarkPrice,
                                   BigDecimal contractMultiplier,
                                   BigDecimal maintenanceMarginRate) {
        if (this.quantity.compareTo(BigDecimal.ZERO) > 0) {
            if (this.side == PositionSide.LONG) {
                this.unrealizedPnl = effectiveMarkPrice.subtract(this.entryPrice)
                                                       .multiply(this.quantity)
                                                       .multiply(contractMultiplier);
            } else {
                this.unrealizedPnl = this.entryPrice.subtract(effectiveMarkPrice)
                                                    .multiply(this.quantity)
                                                    .multiply(contractMultiplier);
            }
            
            BigDecimal notional = effectiveMarkPrice.multiply(this.quantity).multiply(contractMultiplier).abs();
            if (notional.compareTo(BigDecimal.ZERO) == 0) {
                this.marginRatio = BigDecimal.ZERO;
            } else {
                this.marginRatio = this.margin.add(this.unrealizedPnl)
                                              .divide(notional, ValidationConstant.Names.MARGIN_RATIO_SCALE, RoundingMode.HALF_UP);
            }
            
            BigDecimal quantityTimesMultiplier = this.quantity.multiply(contractMultiplier);
            if (quantityTimesMultiplier.compareTo(BigDecimal.ZERO) == 0) {
                this.liquidationPrice = null;
                return;
            }
            if (this.side == PositionSide.LONG) {
                this.liquidationPrice = this.entryPrice
                                                .subtract(this.margin.divide(quantityTimesMultiplier, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP))
                                                .divide(BigDecimal.ONE.subtract(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            } else {
                this.liquidationPrice = this.entryPrice
                                                .add(this.margin.divide(quantityTimesMultiplier, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP))
                                                .divide(BigDecimal.ONE.add(maintenanceMarginRate), ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            }
        } else {
            this.unrealizedPnl = BigDecimal.ZERO;
            this.marginRatio = BigDecimal.ZERO;
            this.liquidationPrice = null;
        }
    }
    
    private BigDecimal calculateRealizedPnl(PositionSide side,
                                            BigDecimal entryPrice,
                                            BigDecimal price,
                                            BigDecimal quantity,
                                            BigDecimal contractMultiplier) {
        if (side == PositionSide.LONG) {
            return price.subtract(entryPrice)
                        .multiply(quantity)
                        .multiply(contractMultiplier);
        }
        return entryPrice.subtract(price)
                         .multiply(quantity)
                         .multiply(contractMultiplier);
    }
    
    public record CloseMetrics(BigDecimal marginReleased, BigDecimal pnl) {
    }
    
    public PositionIntentType evaluateIntent(PositionSide requestSide,
                                             BigDecimal requestedQuantity) {
        BigDecimal requested = requestedQuantity == null ? BigDecimal.ZERO : requestedQuantity;
        if (isSameSide(requestSide)) {
            return PositionIntentType.INCREASE;
        }
        BigDecimal current = quantity == null ? BigDecimal.ZERO : quantity;
        return current.compareTo(requested) > 0 ? PositionIntentType.REDUCE : PositionIntentType.CLOSE;
    }
    
    public static String buildPayload(Position before,
                                      Position after) {
        Position baseline = (before == null || before.getPositionId() == null) ? new Position() : before;
        return OpenObjectDiff.diff(baseline, after);
    }
}
