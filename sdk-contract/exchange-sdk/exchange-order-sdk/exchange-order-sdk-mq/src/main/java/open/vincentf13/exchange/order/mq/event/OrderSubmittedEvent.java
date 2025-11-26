package open.vincentf13.exchange.order.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.common.sdk.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSubmittedEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @org.jetbrains.annotations.NotNull Long instrumentId,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        @NotNull OrderStatus status,
        BigDecimal price,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
        String clientOrderId,
        @NotNull Instant createdAt
) {
}
