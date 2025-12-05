package open.vincentf13.exchange.account.domain.model.transaction;

import open.vincentf13.exchange.account.domain.model.AccountBalance;
import open.vincentf13.exchange.account.domain.model.AccountEntry;

public record AccountWithdrawalResult(
        AccountEntry entry,
        AccountBalance userBalance
) {
}
