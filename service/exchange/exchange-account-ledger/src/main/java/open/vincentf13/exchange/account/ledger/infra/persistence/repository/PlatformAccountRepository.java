package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import open.vincentf13.exchange.account.ledger.domain.model.PlatformAccount;

import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCode;

import java.util.List;
import java.util.Optional;

public interface PlatformAccountRepository {

    PlatformAccount insert(PlatformAccount platformAccount);

    List<PlatformAccount> findBy(PlatformAccount condition);

    Optional<PlatformAccount> findOne(PlatformAccount condition);

    PlatformAccount getOrCreate(PlatformAccountCode code);
}
