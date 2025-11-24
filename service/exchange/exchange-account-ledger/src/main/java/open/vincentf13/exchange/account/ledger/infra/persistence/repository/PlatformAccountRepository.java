package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.PlatformAccountMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformAccountPO;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCategory;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountStatus;
import org.springframework.dao.DuplicateKeyException;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Validated
public class PlatformAccountRepository {

    private final PlatformAccountMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public PlatformAccount insert(@NotNull @Valid PlatformAccount platformAccount) {
        if (platformAccount.getAccountId() == null) {
            platformAccount.setAccountId(idGenerator.newLong());
        }
        PlatformAccountPO po = OpenMapstruct.map(platformAccount, PlatformAccountPO.class);
        if (platformAccount.getAccountCode() != null && po.getAccountCode() == null) {
            po.setAccountCode(platformAccount.getAccountCode().code());
        }
        if (platformAccount.getCategory() != null && po.getCategory() == null) {
            po.setCategory(platformAccount.getCategory().code());
        }
        if (platformAccount.getStatus() != null && po.getStatus() == null) {
            po.setStatus(platformAccount.getStatus().code());
        }
        if (po.getName() == null && platformAccount.getAccountCode() != null) {
            po.setName(platformAccount.getAccountCode().displayName());
        }
        mapper.insertSelective(po);
        return platformAccount;
    }

    public List<PlatformAccount> findBy(@NotNull @Valid PlatformAccount condition) {
        PlatformAccountPO probe = OpenMapstruct.map(condition, PlatformAccountPO.class);
        if (condition.getAccountCode() != null && probe.getAccountCode() == null) {
            probe.setAccountCode(condition.getAccountCode().code());
        }
        if (condition.getCategory() != null && probe.getCategory() == null) {
            probe.setCategory(condition.getCategory().code());
        }
        if (condition.getStatus() != null && probe.getStatus() == null) {
            probe.setStatus(condition.getStatus().code());
        }
        return mapper.findBy(probe).stream()
                .map(item -> OpenMapstruct.map(item, PlatformAccount.class))
                .collect(Collectors.toList());
    }

    public Optional<PlatformAccount> findOne(@NotNull @Valid PlatformAccount condition) {
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
                        return insert(PlatformAccount.builder()
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
