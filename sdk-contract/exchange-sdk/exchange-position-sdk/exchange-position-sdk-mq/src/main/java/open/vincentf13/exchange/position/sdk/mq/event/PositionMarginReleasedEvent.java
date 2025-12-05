package open.vincentf13.exchange.position.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionMarginReleasedEvent(
        @NotNull Long tradeId,
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull AssetSymbol asset,
        @NotNull PositionSide side,
        @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal marginReleased,
        @NotNull BigDecimal realizedPnl,
        @NotNull Instant releasedAt
) {
}
