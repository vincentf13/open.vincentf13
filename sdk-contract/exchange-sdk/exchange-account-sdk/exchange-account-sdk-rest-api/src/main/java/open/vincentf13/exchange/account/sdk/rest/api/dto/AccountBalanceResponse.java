package open.vincentf13.exchange.account.sdk.rest.api.dto;

import java.time.Instant;
import java.util.List;

public record AccountBalanceResponse(
        Long userId,
        Instant snapshotAt,
        List<AccountBalanceItem> balances
) {
}
