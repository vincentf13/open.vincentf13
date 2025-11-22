package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerWithdrawalRequest(
        @NotNull Long userId,
        @NotBlank String asset,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal amount,
        @DecimalMin(value = "0") BigDecimal fee,
        @NotNull LedgerBalanceAccountType accountType,
        Long instrumentId,
        String destination,
        String externalRef,
        Instant requestedAt,
        String metadata
) { }
