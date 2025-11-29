package open.vincentf13.exchange.account.ledger.sdk.mq.event;

import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;

public record FundsFrozenEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull AssetSymbol asset,
        @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal frozenAmount
) {
}
