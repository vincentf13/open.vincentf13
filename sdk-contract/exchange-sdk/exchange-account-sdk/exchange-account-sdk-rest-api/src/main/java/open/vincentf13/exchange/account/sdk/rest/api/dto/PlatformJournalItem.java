package open.vincentf13.exchange.account.sdk.rest.api.dto;

import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;

import java.math.BigDecimal;
import java.time.Instant;

public record PlatformJournalItem(
        Long journalId,
        Long accountId,
        AccountCategory category,
        AssetSymbol asset,
        BigDecimal amount,
        Direction direction,
        BigDecimal balanceAfter,
        ReferenceType referenceType,
        String referenceId,
        String description,
        Instant eventTime,
        Instant createdAt
) {
}
