package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerDepositResponse(
        Long depositId,
        Long entryId,
        BigDecimal balanceAfter,
        Instant creditedAt,
        Long userId,
        AssetSymbol asset,
        BigDecimal amount,
        String txId
) { }
