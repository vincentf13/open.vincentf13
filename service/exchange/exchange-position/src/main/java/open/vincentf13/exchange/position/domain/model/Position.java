package open.vincentf13.exchange.position.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;

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

    public boolean isOpposite(OrderSide orderSide) {
        if (side == null || orderSide == null) {
            return false;
        }
        return side != orderSide;
    }
}
