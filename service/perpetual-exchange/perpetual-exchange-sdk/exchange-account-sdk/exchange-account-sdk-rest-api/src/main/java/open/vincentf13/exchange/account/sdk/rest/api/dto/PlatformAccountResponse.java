package open.vincentf13.exchange.account.sdk.rest.api.dto;

import java.time.Instant;
import java.util.List;

public record PlatformAccountResponse(
    Instant snapshotAt,
    List<PlatformAccountItem> assets,
    List<PlatformAccountItem> liabilities,
    List<PlatformAccountItem> equity,
    List<PlatformAccountItem> expenses,
    List<PlatformAccountItem> revenue) {}
