package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import open.vincentf13.exchange.common.sdk.enums.AccountType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerBalanceItem(
        Long accountId,
        AccountType accountType,
        Long instrumentId,
        AssetSymbol asset,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal reserved,
        BigDecimal totalDeposited,
        BigDecimal totalWithdrawn,
        BigDecimal totalPnl,
        Long lastEntryId,
        Integer version,
        Instant updatedAt
) {
}
