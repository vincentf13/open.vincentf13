package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;

public interface LedgerEntryRepository {

    void insert(LedgerEntry entry);
}
