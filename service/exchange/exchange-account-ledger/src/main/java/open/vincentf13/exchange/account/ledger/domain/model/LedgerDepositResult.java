package open.vincentf13.exchange.account.ledger.domain.model;

public record LedgerDepositResult(
        LedgerEntry userEntry,
        LedgerEntry platformEntry,
        LedgerBalance userBalance,
        PlatformBalance platformBalance
) {
}
