package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import open.vincentf13.exchange.account.ledger.domain.model.PlatformBalance;

import java.util.List;
import java.util.Optional;

public interface PlatformBalanceRepository {

    PlatformBalance insert(PlatformBalance platformBalance);

    boolean updateWithVersion(PlatformBalance platformBalance, Integer expectedVersion);

    List<PlatformBalance> findBy(PlatformBalance condition);

    Optional<PlatformBalance> findOne(PlatformBalance condition);
}
