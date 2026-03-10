package open.vincentf13.exchange.account.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

public record AccountDepositResponse(
    Long depositId,
    Long entryId,
    BigDecimal balanceAfter,
    Instant creditedAt,
    Long userId,
    AssetSymbol asset,
    BigDecimal amount,
    String txId) {}
