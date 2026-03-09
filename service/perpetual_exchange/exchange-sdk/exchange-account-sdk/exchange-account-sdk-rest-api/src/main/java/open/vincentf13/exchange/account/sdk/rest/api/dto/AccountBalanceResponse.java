package open.vincentf13.exchange.account.sdk.rest.api.dto;

import java.time.Instant;

public record AccountBalanceResponse(
    Long userId, Instant snapshotAt, AccountBalanceItem balances) {}
