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
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.account.infra.persistence.mapper.UserAccountMapper;
import open.vincentf13.exchange.account.infra.persistence.po.UserAccountPO;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
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
        if (balance.getAccountName() == null && balance.getAccountCode() != null) {
            balance.setAccountName(balance.getAccountCode().getDisplayName());
        }
        if (balance.getBalance() == null) {
            balance.setBalance(BigDecimal.ZERO);
        }
        if (balance.getAvailable() == null) {
            balance.setAvailable(BigDecimal.ZERO);
        }
        if (balance.getReserved() == null) {
            balance.setReserved(BigDecimal.ZERO);
        }
        if (balance.getVersion() == null) {
            balance.setVersion(0);
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
                                   @NotNull UserAccountCode accountCode,
                                   Long instrumentId,
                                   @NotNull AssetSymbol asset) {
        String accountName = accountCode.getDisplayName();
        AccountCategory category = accountCode.getCategory();
        return findOne(Wrappers.lambdaQuery(UserAccountPO.class)
                               .eq(UserAccountPO::getUserId, userId)
                               .eq(UserAccountPO::getAccountCode, accountCode)
                               .eq(UserAccountPO::getCategory, category)
                               .eq(UserAccountPO::getAsset, asset)
                               .eq(UserAccountPO::getInstrumentId, instrumentId))
                .map(account -> {
                    if (account.getAccountName() == null) {
                        account.setAccountName(accountName);
                    }
                    return account;
                })
                .orElseGet(() -> insertSelective(UserAccount.createDefault(userId, accountCode, instrumentId, asset)));
    }
}
