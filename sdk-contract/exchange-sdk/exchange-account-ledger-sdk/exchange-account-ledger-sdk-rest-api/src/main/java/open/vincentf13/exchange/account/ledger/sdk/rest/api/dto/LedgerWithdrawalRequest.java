package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.sdk.common.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerWithdrawalRequest(
        @NotNull Long userId,
        @NotNull AssetSymbol asset,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal amount,
        @NotBlank String txId,
        @NotNull Instant creditedAt
) {
}
