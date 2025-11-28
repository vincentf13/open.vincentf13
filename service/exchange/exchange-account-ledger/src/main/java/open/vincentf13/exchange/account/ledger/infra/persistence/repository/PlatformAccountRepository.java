package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.PlatformAccountMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformAccountPO;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCategory;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountStatus;
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
public class PlatformAccountRepository {
    
    private final PlatformAccountMapper mapper;
    private final DefaultIdGenerator idGenerator;
    
    public PlatformAccount insertSelective(@NotNull @Valid PlatformAccount platformAccount) {
        if (platformAccount.getAccountId() == null) {
            platformAccount.setAccountId(idGenerator.newLong());
        }
        PlatformAccountPO po = OpenObjectMapper.convert(platformAccount, PlatformAccountPO.class);
        if (po.getName() == null && platformAccount.getAccountCode() != null) {
            po.setName(platformAccount.getAccountCode().displayName());
        }
        mapper.insert(po);
        return platformAccount;
    }
    
    public List<PlatformAccount> findBy(@NotNull LambdaQueryWrapper<PlatformAccountPO> wrapper) {
        return mapper.selectList(wrapper).stream()
                     .map(item -> OpenObjectMapper.convert(item, PlatformAccount.class))
                     .collect(Collectors.toList());
    }
    
    public Optional<PlatformAccount> findOne(@NotNull LambdaQueryWrapper<PlatformAccountPO> wrapper) {
        PlatformAccountPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(OpenObjectMapper.convert(po, PlatformAccount.class));
    }
    
    public PlatformAccount getOrCreate(@NotNull PlatformAccountCode code,
                                       @NotNull PlatformAccountCategory category,
                                       @NotNull PlatformAccountStatus status) {
        PlatformAccount probe = PlatformAccount.builder()
                                               .accountCode(code)
                                               .category(category)
                                               .status(status)
                                               .build();
        return findOne(Wrappers.lambdaQuery(OpenObjectMapper.convert(probe, PlatformAccountPO.class)))
                       .orElseGet(() -> {
                           try {
                               return insertSelective(PlatformAccount.builder()
                                                                     .accountCode(code)
                                                                     .category(category)
                                                                     .status(status)
                                                                     .build());
                           } catch (DuplicateKeyException ex) {
                               return findOne(Wrappers.lambdaQuery(OpenObjectMapper.convert(probe, PlatformAccountPO.class)))
                                              .orElseThrow(() -> ex);
                           }
                       });
    }
}
