package open.vincentf13.exchange.account.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.infra.persistence.mapper.PlatformAccountMapper;
import open.vincentf13.exchange.account.infra.persistence.po.PlatformAccountPO;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@Validated
@RequiredArgsConstructor
public class PlatformAccountRepository {
    
    private final PlatformAccountMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public PlatformAccount insertSelective(@NotNull @Valid PlatformAccount platformAccount) {
        if (platformAccount.getAccountId() == null) {
            platformAccount.setAccountId(idGenerator.newLong());
        }
        if (platformAccount.getAccountName() == null && platformAccount.getAccountCode() != null) {
            platformAccount.setAccountName(platformAccount.getAccountCode().getDisplayName());
        }
        if (platformAccount.getBalance() == null) {
            platformAccount.setBalance(BigDecimal.ZERO);
        }
        if (platformAccount.getVersion() == null) {
            platformAccount.setVersion(0);
        }
        PlatformAccountPO po = OpenObjectMapper.convert(platformAccount, PlatformAccountPO.class);
        mapper.insert(po);
        return platformAccount;
    }
    
    public boolean updateSelectiveBy(@NotNull @Valid PlatformAccount account,
                                     LambdaUpdateWrapper<PlatformAccountPO> updateWrapper) {
        PlatformAccountPO po = OpenObjectMapper.convert(account, PlatformAccountPO.class);
        return mapper.update(po, updateWrapper) > 0;
    }

    public void updateSelectiveBatch(@NotNull List<@Valid PlatformAccount> accounts,
                                     @NotNull List<Integer> expectedVersions,
                                     @NotNull String action) {
        if (accounts.size() != expectedVersions.size()) {
            throw new IllegalArgumentException("accounts size does not match expectedVersions size");
        }
        
        record UpdateTask(PlatformAccountPO po, Integer expectedVersion) {}
        List<UpdateTask> tasks = new java.util.ArrayList<>(accounts.size());
        for (int i = 0; i < accounts.size(); i++) {
            tasks.add(new UpdateTask(OpenObjectMapper.convert(accounts.get(i), PlatformAccountPO.class), expectedVersions.get(i)));
        }
        
        mapper.batchUpdateWithOptimisticLock(tasks);
    }
    
    public List<PlatformAccount> findBy(@NotNull LambdaQueryWrapper<PlatformAccountPO> wrapper) {
        return mapper.selectList(wrapper).stream()
                     .map(item -> OpenObjectMapper.convert(item, PlatformAccount.class))
                     .toList();
    }
    
    public Optional<PlatformAccount> findOne(@NotNull LambdaQueryWrapper<PlatformAccountPO> wrapper) {
        PlatformAccountPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, PlatformAccount.class));
    }

    public List<PlatformAccount> findAll() {
        return findBy(Wrappers.lambdaQuery(PlatformAccountPO.class));
    }

    public List<PlatformAccount> findByAccountIds(@NotNull List<Long> accountIds) {
        Set<Long> uniqueIds = accountIds.stream()
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());
        if (uniqueIds.isEmpty()) {
            return List.of();
        }
        return findBy(Wrappers.lambdaQuery(PlatformAccountPO.class)
                              .in(PlatformAccountPO::getAccountId, uniqueIds));
    }
    
    public PlatformAccount getOrCreate(@NotNull PlatformAccountCode code,
                                       @NotNull AssetSymbol asset) {
        AccountCategory category = code.getCategory();
        String name = code.getDisplayName();
        return findOne(Wrappers.lambdaQuery(PlatformAccountPO.class)
                               .eq(PlatformAccountPO::getAccountCode, code)
                               .eq(PlatformAccountPO::getCategory, category)
                               .eq(PlatformAccountPO::getAsset, asset))
                .map(account -> {
                    if (account.getAccountName() == null) {
                        account.setAccountName(name);
                    }
                    return account;
                })
                .orElseGet(() -> {
                    try {
                        return insertSelective(PlatformAccount.createDefault(code, asset));
                    } catch (DuplicateKeyException ex) {
                        return findOne(Wrappers.lambdaQuery(PlatformAccountPO.class)
                                               .eq(PlatformAccountPO::getAccountCode, code)
                                               .eq(PlatformAccountPO::getCategory, category)
                                               .eq(PlatformAccountPO::getAsset, asset))
                                .orElseThrow(() -> ex);
                    }
                });
    }
}
