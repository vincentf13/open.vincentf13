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
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

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
        PlatformAccountPO po = OpenObjectMapper.convert(platformAccount, PlatformAccountPO.class);
        mapper.insert(po);
        return platformAccount;
    }
    
    public boolean updateSelectiveBy(@NotNull @Valid PlatformAccount account,
                                     LambdaUpdateWrapper<PlatformAccountPO> updateWrapper) {
        PlatformAccountPO po = OpenObjectMapper.convert(account, PlatformAccountPO.class);
        return mapper.update(po, updateWrapper) > 0;
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
    
    public PlatformAccount getOrCreate(@NotNull String code,
                                       @NotNull String name,
                                       @NotNull AccountCategory category,
                                       @NotNull AssetSymbol asset) {
        PlatformAccount probe = PlatformAccount.builder()
                                               .accountCode(code)
                                               .accountName(name)
                                               .category(category)
                                               .asset(asset)
                                               .build();
        return findOne(Wrappers.lambdaQuery(OpenObjectMapper.convert(probe, PlatformAccountPO.class)))
                       .orElseGet(() -> {
                           try {
                               return insertSelective(PlatformAccount.builder()
                                                                     .accountCode(code)
                                                                     .accountName(name)
                                                                     .category(category)
                                                                     .asset(asset)
                                                                     .build());
                           } catch (DuplicateKeyException ex) {
                               return findOne(Wrappers.lambdaQuery(OpenObjectMapper.convert(probe, PlatformAccountPO.class)))
                                              .orElseThrow(() -> ex);
                           }
                       });
    }
}
