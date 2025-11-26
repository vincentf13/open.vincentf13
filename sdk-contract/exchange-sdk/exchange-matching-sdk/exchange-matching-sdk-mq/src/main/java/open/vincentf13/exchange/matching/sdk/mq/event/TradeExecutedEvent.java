package open.vincentf13.exchange.matching.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.sdk.common.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeExecutedEvent(
        @NotNull Long tradeId,
        @NotNull Long orderId,
        @NotNull Long instrumentId,
        @NotNull AssetSymbol quoteAsset,
        @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN) BigDecimal price,
        @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN) BigDecimal quantity,
        @NotNull @DecimalMin(value = ValidationConstant.Names.FEE_MIN) BigDecimal fee,
        @NotNull Instant executedAt
) {
}
