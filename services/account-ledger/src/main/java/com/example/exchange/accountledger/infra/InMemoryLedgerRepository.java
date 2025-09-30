package com.example.exchange.accountledger.infra;

import com.example.exchange.accountledger.domain.AccountBalance;
import com.example.exchange.accountledger.domain.LedgerRepository;
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
