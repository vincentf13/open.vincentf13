package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.EntryType;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.ReferenceType;

import java.util.Optional;

public interface LedgerEntryRepository {

    void insert(LedgerEntry entry);

    Optional<LedgerEntry> findByReference(ReferenceType referenceType, String referenceId, EntryType entryType);
}
