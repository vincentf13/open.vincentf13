package open.vincentf13.exchange.position.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionInvalidFillEvent(
        @NotNull Long tradeId,
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull AssetSymbol asset,
        @NotNull OrderSide side,
        @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN) BigDecimal price,
        @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN) BigDecimal quantity,
        @NotNull @DecimalMin(value = ValidationConstant.Names.FEE_MIN, inclusive = true) BigDecimal feeCharged,
        @NotNull Instant executedAt
) {
}
