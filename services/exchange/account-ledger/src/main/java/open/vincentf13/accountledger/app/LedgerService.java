package open.vincentf13.accountledger.app;

import open.vincentf13.accountledger.domain.AccountBalance;
import open.vincentf13.accountledger.domain.LedgerRepository;
import open.vincentf13.common.open.exchange.accountledger.interfaces.AccountBalanceDto;
import org.springframework.stereotype.Service;

/**
 * Application service orchestrating ledger reads/writes.
 */
@Service
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    public AccountBalanceDto findBalance(String accountId, String asset) {
        AccountBalance balance = ledgerRepository.findBalance(accountId, asset);
        return new AccountBalanceDto(balance.accountId(), balance.asset(), balance.balance().toPlainString());
    }
}
