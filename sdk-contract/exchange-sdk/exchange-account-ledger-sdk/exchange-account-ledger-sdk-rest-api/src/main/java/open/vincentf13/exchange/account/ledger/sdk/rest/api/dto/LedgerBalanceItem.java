package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerBalanceItem(
        Long accountId,
        String accountType,
        Long instrumentId,
        String asset,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal reserved,
        BigDecimal totalDeposited,
        BigDecimal totalWithdrawn,
        BigDecimal totalPnl,
        Long lastEntryId,
        Integer version,
        Instant updatedAt
) { }
