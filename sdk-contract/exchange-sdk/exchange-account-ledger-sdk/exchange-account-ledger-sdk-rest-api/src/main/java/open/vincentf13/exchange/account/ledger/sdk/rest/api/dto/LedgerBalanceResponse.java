package open.vincentf13.exchange.account.ledger.sdk.rest.api.dto;

import java.time.Instant;
import java.util.List;

public record LedgerBalanceResponse(
        Long userId,
        Instant snapshotAt,
        List<LedgerBalanceItem> balances
) {
}
