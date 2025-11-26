package open.vincentf13.exchange.account.ledger.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;

import java.math.BigDecimal;

public record FundsFrozenEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotBlank String asset,
        @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal frozenAmount
) {
}
