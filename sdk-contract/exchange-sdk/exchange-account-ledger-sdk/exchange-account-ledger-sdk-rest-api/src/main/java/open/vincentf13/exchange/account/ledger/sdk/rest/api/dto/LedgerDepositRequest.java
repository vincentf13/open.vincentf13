package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerDepositRequest(
        @NotNull Long userId,
        @NotBlank String asset,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal amount,
        String accountType,
        @NotBlank String txId,
        @NotBlank String channel,
        Instant creditedAt,
        String metadata
) { }
