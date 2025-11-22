package open.vincentf13.exchange.account.ledger.domain.model.transaction;

import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;

public record LedgerWithdrawalResult(
        LedgerEntry entry,
        LedgerBalance userBalance
) {
}
