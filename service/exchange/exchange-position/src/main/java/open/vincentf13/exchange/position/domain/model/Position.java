package open.vincentf13.exchange.position.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentType;

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
    private OrderSide side;
    private BigDecimal quantity;
    private BigDecimal closingReservedQuantity;
    private Instant createdAt;
    private Instant updatedAt;

    public BigDecimal availableToClose() {
        if (quantity == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal reserved = closingReservedQuantity == null ? BigDecimal.ZERO : closingReservedQuantity;
        BigDecimal available = quantity.subtract(reserved);
        return available.max(BigDecimal.ZERO);
    }

    public boolean isSameSide(OrderSide orderSide) {
        if (side == null || orderSide == null) {
            return true;
        }
        return side == orderSide;
    }

    public PositionIntentType evaluateIntent(OrderSide orderSide, BigDecimal requestedQuantity) {
        BigDecimal requested = requestedQuantity == null ? BigDecimal.ZERO : requestedQuantity;
        if (isSameSide(orderSide)) {
            return PositionIntentType.INCREASE;
        }
        BigDecimal current = quantity == null ? BigDecimal.ZERO : quantity;
        return current.compareTo(requested) > 0 ? PositionIntentType.REDUCE : PositionIntentType.CLOSE;
    }
}
