package open.vincentf13.exchange.account.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.Direction;

public record AccountJournalItem(
    Long journalId,
    Long userId,
    Long accountId,
    UserAccountCode accountCode,
    String accountName,
    AccountCategory category,
    AssetSymbol asset,
    BigDecimal amount,
    Direction direction,
    BigDecimal balanceAfter,
    ReferenceType referenceType,
    String referenceId,
    Integer seq,
    String description,
    Instant eventTime,
    Instant createdAt) {}
