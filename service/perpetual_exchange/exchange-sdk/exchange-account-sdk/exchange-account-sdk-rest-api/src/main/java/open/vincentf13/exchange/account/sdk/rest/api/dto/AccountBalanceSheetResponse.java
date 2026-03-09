package open.vincentf13.exchange.account.sdk.rest.api.dto;

import java.time.Instant;
import java.util.List;

public record AccountBalanceSheetResponse(
    Long userId,
    Instant snapshotAt,
    Instant earliestSnapshotAt,
    Instant latestSnapshotAt,
    List<AccountBalanceItem> assets,
    List<AccountBalanceItem> liabilities,
    List<AccountBalanceItem> equity,
    List<AccountBalanceItem> expenses,
    List<AccountBalanceItem> revenue) {}
