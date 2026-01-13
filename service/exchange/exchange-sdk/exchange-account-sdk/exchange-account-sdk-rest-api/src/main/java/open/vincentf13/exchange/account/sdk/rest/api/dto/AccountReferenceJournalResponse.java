package open.vincentf13.exchange.account.sdk.rest.api.dto;

import java.time.Instant;
import java.util.List;
import open.vincentf13.exchange.account.sdk.rest.api.enums.ReferenceType;

public record AccountReferenceJournalResponse(
    Long userId,
    ReferenceType referenceType,
    String referenceIdPrefix,
    Instant snapshotAt,
    List<AccountJournalItem> accountJournals,
    List<PlatformJournalItem> platformJournals) {}
