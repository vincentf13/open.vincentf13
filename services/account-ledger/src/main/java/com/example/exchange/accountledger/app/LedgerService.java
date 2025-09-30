package com.example.exchange.accountledger.app;

import com.example.exchange.accountledger.domain.AccountBalance;
import com.example.exchange.accountledger.domain.LedgerRepository;
import com.example.exchange.api.account.AccountBalanceDto;
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
