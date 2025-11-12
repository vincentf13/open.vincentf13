package open.vincentf13.exchange.order.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderTimeInForce;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private Long id;
    private Long userId;
    private Long instrumentId;
    private String clientOrderId;
    private OrderSide side;
    private OrderType type;
    private OrderStatus status;
    private OrderTimeInForce timeInForce;
    private BigDecimal price;
    private BigDecimal stopPrice;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal avgFillPrice;
    private BigDecimal fee;
    private String source;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;

    public void markStatus(OrderStatus newStatus, Instant updatedAt) {
        this.status = newStatus;
        this.updatedAt = updatedAt;
    }

    public void incrementVersion() {
        if (version == null) {
            version = 0;
        }
        this.version = this.version + 1;
    }
}
