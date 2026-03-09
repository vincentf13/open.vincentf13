package open.vincentf13.exchange.account.sdk.rest.api.dto;

import java.time.Instant;
import java.util.List;

public record AccountJournalResponse(
    Long userId, Long accountId, Instant snapshotAt, List<AccountJournalItem> journals) {}
