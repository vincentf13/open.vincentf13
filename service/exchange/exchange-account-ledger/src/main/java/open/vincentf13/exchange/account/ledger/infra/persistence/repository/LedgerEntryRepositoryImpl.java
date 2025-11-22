package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.LedgerEntryMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.LedgerEntryPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LedgerEntryRepositoryImpl implements LedgerEntryRepository {

    private final LedgerEntryMapper mapper;

    @Override
    public void insert(LedgerEntry entry) {
        LedgerEntryPO po = OpenMapstruct.map(entry, LedgerEntryPO.class);
        if (entry.getOwnerType() != null && po.getOwnerType() == null) {
            po.setOwnerType(entry.getOwnerType().code());
        }
        if (entry.getDirection() != null && po.getDirection() == null) {
            po.setDirection(entry.getDirection().code());
        }
        if (entry.getEntryType() != null && po.getEntryType() == null) {
            po.setEntryType(entry.getEntryType().code());
        }
        if (entry.getAsset() != null && po.getAsset() == null) {
            po.setAsset(entry.getAsset().code());
        }
        if (entry.getReferenceType() != null && po.getReferenceType() == null) {
            po.setReferenceType(entry.getReferenceType().code());
        }
        mapper.insert(po);
    }
}
