package open.vincentf13.exchange.account.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.AccountEntry;
import open.vincentf13.exchange.account.infra.persistence.mapper.AccountEntryMapper;
import open.vincentf13.exchange.account.infra.persistence.po.AccountEntryPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Repository
@Validated
@RequiredArgsConstructor
public class AccountEntryRepository {
    
    private final AccountEntryMapper mapper;
    
    public void insert(@NotNull @Valid AccountEntry entry) {
        AccountEntryPO po = OpenObjectMapper.convert(entry, AccountEntryPO.class);
        mapper.insert(po);
    }
    
    public List<AccountEntry> findBy(@NotNull LambdaQueryWrapper<AccountEntryPO> wrapper) {
        return mapper.selectList(wrapper).stream()
                     .map(item -> OpenObjectMapper.convert(item, AccountEntry.class))
                     .toList();
    }
    
    public Optional<AccountEntry> findOne(@NotNull LambdaQueryWrapper<AccountEntryPO> wrapper) {
        AccountEntryPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, AccountEntry.class));
    }
}
