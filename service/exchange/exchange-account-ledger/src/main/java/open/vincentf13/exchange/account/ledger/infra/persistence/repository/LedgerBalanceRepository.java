package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.LedgerBalanceMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.LedgerBalancePO;
import open.vincentf13.exchange.common.sdk.enums.AccountType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Validated
@RequiredArgsConstructor
public class LedgerBalanceRepository {
    
    private final LedgerBalanceMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public LedgerBalance insertSelective(@NotNull @Valid LedgerBalance balance) {
        if (balance.getId() == null) {
            balance.setId(idGenerator.newLong());
        }
        if (balance.getAccountId() == null) {
            balance.setAccountId(idGenerator.newLong());
        }
        LedgerBalancePO po = OpenObjectMapper.convert(balance, LedgerBalancePO.class);
        mapper.insert(po);
        return balance;
    }
    
    public boolean updateSelectiveBy(@NotNull @Valid LedgerBalance balance,
                                     LambdaUpdateWrapper<LedgerBalancePO> updateWrapper) {
        LedgerBalancePO po = OpenObjectMapper.convert(balance, LedgerBalancePO.class);
        return mapper.update(po, updateWrapper) > 0;
    }
    
    public List<LedgerBalance> findBy(@NotNull LambdaQueryWrapper<LedgerBalancePO> wrapper) {
        return mapper.selectList(wrapper).stream()
                     .map(item -> OpenObjectMapper.convert(item, LedgerBalance.class))
                     .collect(Collectors.toList());
    }
    
    public Optional<LedgerBalance> findOne(@NotNull LambdaQueryWrapper<LedgerBalancePO> wrapper) {
        LedgerBalancePO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, LedgerBalance.class));
    }
    
    public LedgerBalance getOrCreate(@NotNull Long userId,
                                     @NotNull AccountType accountType,
                                     Long instrumentId,
                                     @NotNull AssetSymbol asset) {
        LedgerBalance probe = LedgerBalance.builder()
                                           .userId(userId)
                                           .accountType(accountType)
                                           .instrumentId(instrumentId)
                                           .asset(asset)
                                           .build();
        return findOne(Wrappers.lambdaQuery(OpenObjectMapper.convert(probe, LedgerBalancePO.class)))
                       .orElseGet(() -> insertSelective(LedgerBalance.createDefault(userId, accountType, instrumentId, asset)));
    }
}
