package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformBalance;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.PlatformBalanceMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformBalancePO;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.sdk.common.enums.AssetSymbol;
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
@Validated
@RequiredArgsConstructor
public class PlatformBalanceRepository {

    private final PlatformBalanceMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public PlatformBalance insert(@NotNull @Valid PlatformBalance platformBalance) {
        if (platformBalance.getId() == null) {
            platformBalance.setId(idGenerator.newLong());
        }
        if (platformBalance.getCreatedAt() == null) {
            platformBalance.setCreatedAt(Instant.now());
        }
        if (platformBalance.getUpdatedAt() == null) {
            platformBalance.setUpdatedAt(platformBalance.getCreatedAt());
        }
        if (platformBalance.getVersion() == null) {
            platformBalance.setVersion(0);
        }
        PlatformBalancePO po = OpenMapstruct.map(platformBalance, PlatformBalancePO.class);
        if (platformBalance.getAccountCode() != null && po.getAccountCode() == null) {
            po.setAccountCode(platformBalance.getAccountCode().code());
        }
        if (platformBalance.getAsset() != null && po.getAsset() == null) {
            po.setAsset(platformBalance.getAsset().code());
        }
        mapper.insertSelective(po);
        if (po.getCreatedAt() != null) {
            platformBalance.setCreatedAt(po.getCreatedAt());
        }
        if (po.getUpdatedAt() != null) {
            platformBalance.setUpdatedAt(po.getUpdatedAt());
        }
        return platformBalance;
    }

    public boolean updateWithVersion(@NotNull @Valid PlatformBalance platformBalance, Integer expectedVersion) {
        platformBalance.setUpdatedAt(Instant.now());
        PlatformBalancePO po = OpenMapstruct.map(platformBalance, PlatformBalancePO.class);
        if (platformBalance.getAccountCode() != null && po.getAccountCode() == null) {
            po.setAccountCode(platformBalance.getAccountCode().code());
        }
        if (platformBalance.getAsset() != null && po.getAsset() == null) {
            po.setAsset(platformBalance.getAsset().code());
        }
        return mapper.updateByIdAndVersion(po, expectedVersion) > 0;
    }

    public List<PlatformBalance> findBy(@NotNull @Valid PlatformBalance condition) {
        PlatformBalancePO probe = OpenMapstruct.map(condition, PlatformBalancePO.class);
        if (condition.getAccountCode() != null && probe.getAccountCode() == null) {
            probe.setAccountCode(condition.getAccountCode().code());
        }
        if (condition.getAsset() != null && probe.getAsset() == null) {
            probe.setAsset(condition.getAsset().code());
        }
        return mapper.findBy(probe).stream()
                .map(item -> OpenMapstruct.map(item, PlatformBalance.class))
                .collect(Collectors.toList());
    }

    public Optional<PlatformBalance> findOne(@NotNull @Valid PlatformBalance condition) {
        List<PlatformBalance> results = findBy(condition);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single platform balance but found " + results.size());
        }
        return Optional.of(results.get(0));
    }

    public PlatformBalance getOrCreate(@NotNull Long accountId, @NotNull PlatformAccountCode accountCode, @NotNull AssetSymbol asset) {
        PlatformBalance probe = PlatformBalance.builder()
                .accountId(accountId)
                .accountCode(accountCode)
                .asset(asset)
                .build();
        return findOne(probe)
                .orElseGet(() -> {
                    try {
                        return insert(PlatformBalance.createDefault(accountId, accountCode, asset));
                    } catch (DuplicateKeyException ex) {
                        return findOne(probe)
                                .orElseThrow(() -> ex);
                    }
                });
    }
}
