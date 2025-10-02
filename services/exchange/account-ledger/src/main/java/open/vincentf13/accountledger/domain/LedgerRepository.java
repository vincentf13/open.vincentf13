package open.vincentf13.accountledger.domain;

/**
 * Domain repository abstraction for ledger storage.
 */
public interface LedgerRepository {

    AccountBalance findBalance(String accountId, String asset);
}
