package open.vincentf13.exchange.order.sdk.rest.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderSide;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderTimeInForce;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderType;

import java.math.BigDecimal;

public record OrderCreateRequest(
        @NotNull(message = "instrumentId is required")
        Long instrumentId,

        @NotNull(message = "side is required")
        OrderSide side,

        @NotNull(message = "type is required")
        OrderType type,

        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.00000001", inclusive = true, message = "quantity must be positive")
        BigDecimal quantity,

        @DecimalMin(value = "0.00000001", inclusive = true, message = "price must be positive")
        BigDecimal price,

        @DecimalMin(value = "0.00000001", inclusive = true, message = "stopPrice must be positive")
        BigDecimal stopPrice,

        OrderTimeInForce timeInForce,

        @Size(max = 64, message = "clientOrderId length must be <= 64")
        String clientOrderId,

        @Size(max = 50, message = "source length must be <= 50")
        String source
) {
}
