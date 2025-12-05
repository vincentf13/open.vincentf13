package open.vincentf13.exchange.account.sdk.rest.api.dto;

import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountBalanceItem(
        Long accountId,
        String accountCode,
        String accountName,
        AccountCategory category,
        Long instrumentId,
        AssetSymbol asset,
        BigDecimal balance,
        BigDecimal available,
        BigDecimal reserved,
        Integer version,
        Instant updatedAt
) {
}
