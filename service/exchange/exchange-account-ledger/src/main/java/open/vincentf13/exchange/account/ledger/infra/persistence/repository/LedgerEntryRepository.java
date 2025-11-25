package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerEntry;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.LedgerEntryMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.LedgerEntryPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Repository
@Validated
@RequiredArgsConstructor
public class LedgerEntryRepository {

    private final LedgerEntryMapper mapper;

    public void insert(@NotNull @Valid LedgerEntry entry) {
        LedgerEntryPO po = OpenObjectMapper.convert(entry, LedgerEntryPO.class);
        mapper.insertSelective(po);
    }

    public List<LedgerEntry> findBy(@NotNull LedgerEntry condition) {
        LedgerEntryPO probe = OpenObjectMapper.convert(condition, LedgerEntryPO.class);
        return mapper.findBy(probe).stream()
                .map(item -> OpenObjectMapper.convert(item, LedgerEntry.class))
                .toList();
    }

    public Optional<LedgerEntry> findOne(@NotNull LedgerEntry condition) {
        LedgerEntryPO probe = OpenObjectMapper.convert(condition, LedgerEntryPO.class);
        List<LedgerEntryPO> results = mapper.findBy(probe);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single ledger entry but found " + results.size());
        }
        return Optional.of(OpenObjectMapper.convert(results.get(0), LedgerEntry.class));
    }

}
