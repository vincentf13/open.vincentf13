package open.vincentf13.exchange.account.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

public record PlatformAccountItem(
    Long accountId,
    PlatformAccountCode accountCode,
    String accountName,
    AccountCategory category,
    AssetSymbol asset,
    BigDecimal balance,
    Integer version,
    Instant createdAt,
    Instant updatedAt) {}
