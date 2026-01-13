package open.vincentf13.exchange.account.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

public record AccountBalanceItem(
    Long accountId,
    UserAccountCode accountCode,
    String accountName,
    AccountCategory category,
    Long instrumentId,
    AssetSymbol asset,
    BigDecimal balance,
    BigDecimal available,
    BigDecimal reserved,
    Integer version,
    Instant createdAt,
    Instant updatedAt) {}
