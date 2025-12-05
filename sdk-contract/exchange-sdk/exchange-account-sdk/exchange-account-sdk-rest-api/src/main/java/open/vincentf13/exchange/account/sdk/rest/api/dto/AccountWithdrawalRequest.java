package open.vincentf13.exchange.account.sdk.rest.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountWithdrawalRequest(
        @NotNull Long userId,
        @NotNull AssetSymbol asset,
        @NotNull @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN) BigDecimal amount,
        @NotBlank String txId,
        @NotNull Instant creditedAt
) {
}
