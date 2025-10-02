package open.vincentf13.accountledger.infra;

import open.vincentf13.accountledger.domain.AccountBalance;
import open.vincentf13.accountledger.domain.LedgerRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;

/**
 * Temporary in-memory repository placeholder.
 */
@Repository
public class InMemoryLedgerRepository implements LedgerRepository {

    @Override
    public AccountBalance findBalance(String accountId, String asset) {
        return new AccountBalance(accountId, asset, BigDecimal.ZERO);
    }
}
