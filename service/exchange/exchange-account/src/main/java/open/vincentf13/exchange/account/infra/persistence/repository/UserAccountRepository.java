package open.vincentf13.exchange.account.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.infra.persistence.mapper.UserAccountMapper;
import open.vincentf13.exchange.account.infra.persistence.po.UserAccountPO;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Repository
@Validated
@RequiredArgsConstructor
public class UserAccountRepository {
    
    private final UserAccountMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public UserAccount insertSelective(@NotNull @Valid UserAccount balance) {
        if (balance.getAccountId() == null) {
            balance.setAccountId(idGenerator.newLong());
        }
        UserAccountPO po = OpenObjectMapper.convert(balance, UserAccountPO.class);
        mapper.insert(po);
        return balance;
    }
    
    public boolean updateSelectiveBy(@NotNull @Valid UserAccount balance,
                                     LambdaUpdateWrapper<UserAccountPO> updateWrapper) {
        UserAccountPO po = OpenObjectMapper.convert(balance, UserAccountPO.class);
        return mapper.update(po, updateWrapper) > 0;
    }
    
    public List<UserAccount> findBy(@NotNull LambdaQueryWrapper<UserAccountPO> wrapper) {
        return mapper.selectList(wrapper).stream()
                     .map(item -> OpenObjectMapper.convert(item, UserAccount.class))
                     .toList();
    }
    
    public Optional<UserAccount> findOne(@NotNull LambdaQueryWrapper<UserAccountPO> wrapper) {
        UserAccountPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, UserAccount.class));
    }
    
    public UserAccount getOrCreate(@NotNull Long userId,
                                   @NotNull String accountCode,
                                   @NotNull String accountName,
                                   Long instrumentId,
                                   @NotNull AccountCategory category,
                                   @NotNull AssetSymbol asset) {
        UserAccount probe = UserAccount.builder()
                                       .userId(userId)
                                       .accountCode(accountCode)
                                       .accountName(accountName)
                                       .instrumentId(instrumentId)
                                       .category(category)
                                       .asset(asset)
                                       .build();
        return findOne(Wrappers.lambdaQuery(OpenObjectMapper.convert(probe, UserAccountPO.class)))
                .orElseGet(() -> insertSelective(UserAccount.createDefault(userId, accountCode, accountName, instrumentId, category, asset)));
    }
}
