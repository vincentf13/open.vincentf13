package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AccountType;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AssetSymbol;

import java.util.List;
import java.util.Optional;

public interface LedgerBalanceRepository {

    LedgerBalance insert(LedgerBalance balance);

    boolean updateWithVersion(LedgerBalance balance, Integer expectedVersion);

    List<LedgerBalance> findBy(LedgerBalance condition);

    Optional<LedgerBalance> findOne(LedgerBalance condition);
    LedgerBalance getOrCreate(Long userId, AccountType accountType, Long instrumentId, AssetSymbol asset);
}
