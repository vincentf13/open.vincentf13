package open.vincentf13.exchange.account.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

public record FundsFrozenEvent(
    @NotNull Long orderId,
    @NotNull Long userId,
    @NotNull Long instrumentId,
    @NotNull AssetSymbol asset,
    @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal amount,
    @NotNull Instant eventTime) {}
