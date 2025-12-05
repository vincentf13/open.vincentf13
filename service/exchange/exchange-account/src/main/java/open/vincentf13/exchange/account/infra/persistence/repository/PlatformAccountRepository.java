package open.vincentf13.exchange.account.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.infra.AccountErrorCode;
import open.vincentf13.exchange.account.infra.persistence.mapper.PlatformAccountMapper;
import open.vincentf13.exchange.account.infra.persistence.po.PlatformAccountPO;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.infra.mysql.OpenMybatisBatchExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
        record UpdateCommand(PlatformAccountPO po, LambdaUpdateWrapper<PlatformAccountPO> wrapper) { }
        List<UpdateCommand> commands = new java.util.ArrayList<>(accounts.size());
        for (int i = 0; i < accounts.size(); i++) {
            PlatformAccount account = accounts.get(i);
            Integer expectedVersion = expectedVersions.get(i);
            PlatformAccountPO po = OpenObjectMapper.convert(account, PlatformAccountPO.class);
            LambdaUpdateWrapper<PlatformAccountPO> wrapper = new LambdaUpdateWrapper<PlatformAccountPO>()
                    .eq(PlatformAccountPO::getAccountId, account.getAccountId())
                    .eq(PlatformAccountPO::getVersion, expectedVersion);
            commands.add(new UpdateCommand(po, wrapper));
        }
        OpenMybatisBatchExecutor.execute(PlatformAccountMapper.class, commands, (m, cmd) -> {
            if (m.update(cmd.po(), cmd.wrapper()) == 0) {
                throw OpenException.of(AccountErrorCode.OPTIMISTIC_LOCK_FAILURE, java.util.Map.of("action", action, "accountId", cmd.po().getAccountId()));
            }
        });
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
