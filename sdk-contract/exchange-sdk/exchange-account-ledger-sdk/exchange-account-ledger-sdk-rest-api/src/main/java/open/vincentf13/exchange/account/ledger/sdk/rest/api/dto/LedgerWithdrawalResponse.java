package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerWithdrawalResponse(
        Long withdrawalId,
        Long reservedEntryId,
        String status,
        BigDecimal balanceAfter,
        Instant requestedAt,
        Long userId,
        String asset,
        BigDecimal amount,
        BigDecimal fee,
        String externalRef
) { }
