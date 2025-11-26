package open.vincentf13.exchange.position.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;

import java.math.BigDecimal;
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
    @Min(1)
    private Integer leverage;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal margin;
    @NotNull
    private PositionSide side;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal entryPrice;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal quantity;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal closingReservedQuantity;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal markPrice;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal marginRatio;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal unrealizedPnl;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal realizedPnl;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal liquidationPrice;
    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal bankruptcyPrice;
    @NotNull
    private String status;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;

    public int safeVersion() {
        return version == null ? 0 : version;
    }

    public BigDecimal availableToClose() {
        if (quantity == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal reserved = closingReservedQuantity == null ? BigDecimal.ZERO : closingReservedQuantity;
        BigDecimal available = quantity.subtract(reserved);
        return available.max(BigDecimal.ZERO);
    }

    public boolean isSameSide(PositionSide otherSide) {
        if (side == null || otherSide == null) {
            return true;
        }
        return side == otherSide;
    }

    public PositionIntentType evaluateIntent(PositionSide requestSide, BigDecimal requestedQuantity) {
        BigDecimal requested = requestedQuantity == null ? BigDecimal.ZERO : requestedQuantity;
        if (isSameSide(requestSide)) {
            return PositionIntentType.INCREASE;
        }
        BigDecimal current = quantity == null ? BigDecimal.ZERO : quantity;
        return current.compareTo(requested) > 0 ? PositionIntentType.REDUCE : PositionIntentType.CLOSE;
    }

    public static Position createDefault(Long userId, Long instrumentId, PositionSide side) {
        return Position.builder()
                .userId(userId)
                .instrumentId(instrumentId)
                .leverage(40)
                .margin(BigDecimal.ZERO)
                .side(side == null ? PositionSide.LONG : side)
                .entryPrice(BigDecimal.ZERO)
                .quantity(BigDecimal.ZERO)
                .closingReservedQuantity(BigDecimal.ZERO)
                .markPrice(BigDecimal.ZERO)
                .marginRatio(BigDecimal.ZERO)
                .unrealizedPnl(BigDecimal.ZERO)
                .realizedPnl(BigDecimal.ZERO)
                .liquidationPrice(BigDecimal.ZERO)
                .bankruptcyPrice(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();
    }
}
