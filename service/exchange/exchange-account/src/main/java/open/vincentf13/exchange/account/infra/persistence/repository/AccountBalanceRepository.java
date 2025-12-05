package open.vincentf13.exchange.account.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.AccountBalance;
import open.vincentf13.exchange.account.infra.persistence.mapper.AccountBalanceMapper;
import open.vincentf13.exchange.account.infra.persistence.po.AccountBalancePO;
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
public class AccountBalanceRepository {
    
    private final AccountBalanceMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public AccountBalance insertSelective(@NotNull @Valid AccountBalance balance) {
        if (balance.getId() == null) {
            balance.setId(idGenerator.newLong());
        }
        if (balance.getAccountId() == null) {
            balance.setAccountId(idGenerator.newLong());
        }
        AccountBalancePO po = OpenObjectMapper.convert(balance, AccountBalancePO.class);
        mapper.insert(po);
        return balance;
    }
    
    public boolean updateSelectiveBy(@NotNull @Valid AccountBalance balance,
                                     LambdaUpdateWrapper<AccountBalancePO> updateWrapper) {
        AccountBalancePO po = OpenObjectMapper.convert(balance, AccountBalancePO.class);
        return mapper.update(po, updateWrapper) > 0;
    }
    
    public List<AccountBalance> findBy(@NotNull LambdaQueryWrapper<AccountBalancePO> wrapper) {
        return mapper.selectList(wrapper).stream()
                     .map(item -> OpenObjectMapper.convert(item, AccountBalance.class))
                     .collect(Collectors.toList());
    }
    
    public Optional<AccountBalance> findOne(@NotNull LambdaQueryWrapper<AccountBalancePO> wrapper) {
        AccountBalancePO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, AccountBalance.class));
    }
    
    public AccountBalance getOrCreate(@NotNull Long userId,
                                     @NotNull AccountType accountType,
                                     Long instrumentId,
                                     @NotNull AssetSymbol asset) {
        AccountBalance probe = AccountBalance.builder()
                                           .userId(userId)
                                           .accountType(accountType)
                                           .instrumentId(instrumentId)
                                           .asset(asset)
                                           .build();
        return findOne(Wrappers.lambdaQuery(OpenObjectMapper.convert(probe, AccountBalancePO.class)))
                       .orElseGet(() -> insertSelective(AccountBalance.createDefault(userId, accountType, instrumentId, asset)));
    }
}
