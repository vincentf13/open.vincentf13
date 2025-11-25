package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

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
import open.vincentf13.sdk.core.OpenObjectMapper;
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
        mapper.insertSelective(po);
        return platformAccount;
    }

    public List<PlatformAccount> findBy(@NotNull PlatformAccount condition) {
        PlatformAccountPO probe = OpenObjectMapper.convert(condition, PlatformAccountPO.class);
        return mapper.findBy(probe).stream()
                .map(item -> OpenObjectMapper.convert(item, PlatformAccount.class))
                .collect(Collectors.toList());
    }

    public Optional<PlatformAccount> findOne(@NotNull PlatformAccount condition) {
        List<PlatformAccount> results = findBy(condition);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single platform account but found " + results.size());
        }
        return Optional.of(results.get(0));
    }

    public PlatformAccount getOrCreate(@NotNull PlatformAccountCode code) {
        PlatformAccount probe = PlatformAccount.builder()
                .accountCode(code)
                .build();
        return findOne(probe)
                .orElseGet(() -> {
                    try {
                        return insertSelective(PlatformAccount.builder()
                                .accountCode(code)
                                .category(PlatformAccountCategory.LIABILITY)
                                .status(PlatformAccountStatus.ACTIVE)
                                .build());
                    } catch (DuplicateKeyException ex) {
                        return findOne(probe)
                                .orElseThrow(() -> ex);
                    }
                });
    }
}
