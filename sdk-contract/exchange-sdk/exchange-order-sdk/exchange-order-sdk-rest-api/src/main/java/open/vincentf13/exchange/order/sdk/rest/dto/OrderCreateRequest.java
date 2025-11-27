package open.vincentf13.exchange.order.sdk.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderType;

import java.math.BigDecimal;

public record OrderCreateRequest(
        @NotNull(message = "instrumentId is required")
        Long instrumentId,

        @NotNull(message = "side is required")
        OrderSide side,

        @NotNull(message = "type is required")
        OrderType type,

        @NotNull(message = "quantity is required")
        @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = true, message = "quantity must be positive")
        BigDecimal quantity,

        @DecimalMin(value = ValidationConstant.Names.PRICE_MIN, inclusive = true, message = "price must be positive")
        BigDecimal price,

        @Size(max = 64, message = "clientOrderId length must be <= 64")
        String clientOrderId
) {
}
