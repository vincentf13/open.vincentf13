package open.vincentf13.exchange.account.ledger.sdk.mq.event;

import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.EntryType;
import open.vincentf13.exchange.common.sdk.enums.ReferenceType;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryCreatedEvent(
        Long entryId,
        Long userId,
        AssetSymbol asset,
        BigDecimal deltaBalance,
        BigDecimal balanceAfter,
        ReferenceType referenceType,
        String referenceId,
        EntryType entryType,
        Long instrumentId,
        Instant eventTime
) {
}