package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformBalance;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.PlatformBalanceMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformBalancePO;
import open.vincentf13.exchange.common.sdk.enums.PlatformAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Validated
@RequiredArgsConstructor
public class PlatformBalanceRepository {
    
    private final PlatformBalanceMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public PlatformBalance insertSelective(@NotNull @Valid PlatformBalance platformBalance) {
        if (platformBalance.getId() == null) {
            platformBalance.setId(idGenerator.newLong());
        }
        if (platformBalance.getVersion() == null) {
            platformBalance.setVersion(0);
        }
        PlatformBalancePO po = OpenObjectMapper.convert(platformBalance, PlatformBalancePO.class);
        mapper.insert(po);
        return platformBalance;
    }
    
    public boolean updateSelectiveBy(@NotNull @Valid PlatformBalance platformBalance,
                                     LambdaUpdateWrapper<PlatformBalancePO> updateWrapper) {
        PlatformBalancePO po = OpenObjectMapper.convert(platformBalance, PlatformBalancePO.class);
        return mapper.update(po, updateWrapper) > 0;
    }
    
    public List<PlatformBalance> findBy(@NotNull LambdaQueryWrapper<PlatformBalancePO> wrapper) {
        return mapper.selectList(wrapper).stream()
                     .map(item -> OpenObjectMapper.convert(item, PlatformBalance.class))
                     .collect(Collectors.toList());
    }
    
    public Optional<PlatformBalance> findOne(@NotNull LambdaQueryWrapper<PlatformBalancePO> wrapper) {
        PlatformBalancePO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, PlatformBalance.class));
    }
    
    public PlatformBalance getOrCreate(@NotNull Long accountId,
                                       @NotNull PlatformAccountCode accountCode,
                                       @NotNull AssetSymbol asset) {
        LambdaQueryWrapper<PlatformBalancePO> wrapper = Wrappers.lambdaQuery(PlatformBalancePO.class)
                                                                .eq(PlatformBalancePO::getAccountId, accountId)
                                                                .eq(PlatformBalancePO::getAccountCode, accountCode)
                                                                .eq(PlatformBalancePO::getAsset, asset);
        return findOne(wrapper)
                       .orElseGet(() -> {
                           try {
                               return insertSelective(PlatformBalance.createDefault(accountId, accountCode, asset));
                           } catch (DuplicateKeyException ex) {
                               return findOne(wrapper)
                                              .orElseThrow(() -> ex);
                           }
                       });
    }
}
