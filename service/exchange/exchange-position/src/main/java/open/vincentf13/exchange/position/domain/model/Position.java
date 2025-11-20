package open.vincentf13.exchange.position.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentType;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    private Long positionId;
    private Long userId;
    private Long instrumentId;
    private Integer leverage;
    private BigDecimal margin;
    private PositionSide side;
    private BigDecimal entryPrice;
    private BigDecimal quantity;
    private BigDecimal closingReservedQuantity;
    private BigDecimal markPrice;
    private BigDecimal marginRatio;
    private BigDecimal unrealizedPnl;
    private BigDecimal realizedPnl;
    private BigDecimal liquidationPrice;
    private BigDecimal bankruptcyPrice;
    private String status;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;

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
}
