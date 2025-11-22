package open.vincentf13.exchange.account.ledger.application.result;

import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformBalance;

public record LedgerDepositResult(
        LedgerEntry userEntry,
        LedgerEntry platformEntry,
        LedgerBalance userBalance,
        PlatformBalance platformBalance
) {
}
