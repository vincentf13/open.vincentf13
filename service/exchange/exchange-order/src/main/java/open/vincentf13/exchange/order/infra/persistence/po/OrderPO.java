package open.vincentf13.exchange.order.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderSide;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderType;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionIntentType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPO {
    private Long orderId;
    private Long userId;
    private Long instrumentId;
    private String clientOrderId;
    private OrderSide side;
    private PositionIntentType intent;
    private BigDecimal closeCostPrice;
    private OrderType type;
    private OrderStatus status;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal avgFillPrice;
    private BigDecimal fee;
    private Integer version;
    private Integer expectedVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
    private Instant filledAt;
}
