package open.vincentf13.exchange.order.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;

import java.math.BigDecimal;
import java.time.Instant;

public record FundsFreezeRequestedEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal requiredMargin,
        @NotNull @DecimalMin(value = ValidationConstant.Names.FEE_MIN, inclusive = true) BigDecimal fee,
        @NotNull Instant createdAt
) {
}
