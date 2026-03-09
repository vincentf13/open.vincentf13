package open.vincentf13.exchange.account.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;

public record TradeMarginSettledEvent(
    @NotNull Long tradeId,
    @NotNull Long orderId,
    @NotNull Long userId,
    @NotNull Long instrumentId,
    @NotNull AssetSymbol asset,
    @NotNull OrderSide side,
    @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN) BigDecimal price,
    @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN) BigDecimal quantity,
    @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal marginUsed,
    @NotNull @DecimalMin(value = ValidationConstant.Names.FEE_MIN, inclusive = true)
        BigDecimal feeCharged,
    @NotNull Instant executedAt,
    @NotNull Instant settledAt,
    boolean isRecursive) {}
