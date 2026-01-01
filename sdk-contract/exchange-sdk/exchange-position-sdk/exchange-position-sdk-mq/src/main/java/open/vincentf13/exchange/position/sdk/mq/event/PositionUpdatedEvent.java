package open.vincentf13.exchange.position.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionUpdatedEvent(
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull PositionSide side,
        @NotNull BigDecimal quantity,
        @NotNull @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE) BigDecimal entryPrice,
        @NotNull @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE) BigDecimal markPrice,
        @NotNull BigDecimal unrealizedPnl,
        @NotNull @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE) BigDecimal liquidationPrice,
        @NotNull Instant timestamp
) {
}
