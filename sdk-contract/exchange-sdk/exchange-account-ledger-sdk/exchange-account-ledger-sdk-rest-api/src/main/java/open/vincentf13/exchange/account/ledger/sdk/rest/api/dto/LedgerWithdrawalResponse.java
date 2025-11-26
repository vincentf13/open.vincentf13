package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerWithdrawalResponse(
        Long withdrawalId,
        Long reservedEntryId,
        BigDecimal balanceAfter,
        Instant requestedAt,
        Long userId,
        AssetSymbol asset,
        BigDecimal amount,
        String txId
) { }
