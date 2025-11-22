package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerDepositResponse(
        Long depositId,
        Long entryId,
        String status,
        BigDecimal balanceAfter,
        Instant creditedAt,
        Long userId,
        String asset,
        BigDecimal amount,
        String txId,
        String channel
) { }
