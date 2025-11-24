package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.LedgerEntryMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.LedgerEntryPO;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.EntryType;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.ReferenceType;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Repository
@Validated
@RequiredArgsConstructor
public class LedgerEntryRepository {

    private final LedgerEntryMapper mapper;

    public void insert(@NotNull LedgerEntry entry) {
        LedgerEntryPO po = OpenMapstruct.map(entry, LedgerEntryPO.class);
        mapper.insertSelective(po);
    }

    public Optional<LedgerEntry> findOne(@NotNull LedgerEntry condition) {
        LedgerEntryPO probe = OpenMapstruct.map(condition, LedgerEntryPO.class);
        List<LedgerEntryPO> results = mapper.findBy(probe);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single ledger entry but found " + results.size());
        }
        return Optional.of(OpenMapstruct.map(results.get(0), LedgerEntry.class));
    }

    public Optional<LedgerEntry> findByReference(@NotNull ReferenceType referenceType,
                                                 @NotNull String referenceId,
                                                 EntryType entryType) {
        LedgerEntry condition = LedgerEntry.builder()
                .referenceType(referenceType)
                .referenceId(referenceId)
                .entryType(entryType)
                .build();
        return findOne(condition);
    }
}
