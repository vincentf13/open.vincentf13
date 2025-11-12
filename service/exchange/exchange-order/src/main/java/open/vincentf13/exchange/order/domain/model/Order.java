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
import java.math.RoundingMode;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private Long orderId;
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

    public void applyTrade(BigDecimal tradeQuantity, BigDecimal tradePrice, BigDecimal tradeFee, Instant executedAt) {
        BigDecimal qty = normalize(tradeQuantity);
        if (qty.signum() <= 0) {
            return;
        }
        Instant now = executedAt != null ? executedAt : Instant.now();
        BigDecimal originalFilled = normalize(this.filledQuantity);
        BigDecimal newFilled = originalFilled.add(qty);
        BigDecimal orderQuantity = normalize(this.quantity);
        BigDecimal remaining = orderQuantity.subtract(newFilled);
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
            newFilled = orderQuantity;
        }

        BigDecimal avgPrice = calculateWeightedAverage(originalFilled, normalize(this.avgFillPrice), qty, normalize(tradePrice));
        BigDecimal updatedFee = normalize(this.fee).add(normalize(tradeFee));

        this.filledQuantity = newFilled;
        this.remainingQuantity = remaining;
        this.avgFillPrice = avgPrice;
        this.fee = updatedFee;
        this.updatedAt = now;
        this.status = remaining.signum() == 0 ? OrderStatus.FILLED : OrderStatus.PARTIAL_FILLED;
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal calculateWeightedAverage(BigDecimal existingQty, BigDecimal existingAvgPrice,
                                                       BigDecimal tradeQty, BigDecimal tradePrice) {
        BigDecimal totalQty = existingQty.add(tradeQty);
        if (totalQty.signum() == 0) {
            return null;
        }
        BigDecimal existingValue = existingAvgPrice.multiply(existingQty);
        BigDecimal tradeValue = tradePrice.multiply(tradeQty);
        BigDecimal totalValue = existingValue.add(tradeValue);
        return totalValue.divide(totalQty, 18, RoundingMode.HALF_UP);
    }
}
