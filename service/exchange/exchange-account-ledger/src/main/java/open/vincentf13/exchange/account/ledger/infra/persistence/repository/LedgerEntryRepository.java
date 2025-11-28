package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        mapper.insert(po);
    }
    
    public List<LedgerEntry> findBy(@NotNull LambdaQueryWrapper<LedgerEntryPO> wrapper) {
        return mapper.selectList(wrapper).stream()
                     .map(item -> OpenObjectMapper.convert(item, LedgerEntry.class))
                     .toList();
    }
    
    public Optional<LedgerEntry> findOne(@NotNull LambdaQueryWrapper<LedgerEntryPO> wrapper) {
        LedgerEntryPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, LedgerEntry.class));
    }
}
