package open.vincentf13.exchange.account.ledger.sdk.mq.event;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryCreatedEvent(
        Long entryId,
        Long userId,
        String asset,
        BigDecimal deltaBalance,
        BigDecimal balanceAfter,
        String referenceType,
        String referenceId,
        String entryType,
        Long instrumentId,
        Instant eventTime
) {
}