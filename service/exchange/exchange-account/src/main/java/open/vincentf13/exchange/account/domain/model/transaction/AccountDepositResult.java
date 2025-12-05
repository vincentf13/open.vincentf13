package open.vincentf13.exchange.account.domain.model.transaction;

import open.vincentf13.exchange.account.domain.model.AccountBalance;
import open.vincentf13.exchange.account.domain.model.AccountEntry;
import open.vincentf13.exchange.account.domain.model.PlatformBalance;

public record AccountDepositResult(
        AccountEntry userEntry,
        AccountEntry platformEntry,
        AccountBalance userBalance,
        PlatformBalance platformBalance
) {
}
