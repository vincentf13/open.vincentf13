package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformBalance;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.PlatformBalanceMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformBalancePO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PlatformBalanceRepositoryImpl implements PlatformBalanceRepository {

    private final PlatformBalanceMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public PlatformBalance insert(PlatformBalance platformBalance) {
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

    @Override
    public boolean updateWithVersion(PlatformBalance platformBalance, Integer expectedVersion) {
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

    @Override
    public List<PlatformBalance> findBy(PlatformBalance condition) {
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

    @Override
    public Optional<PlatformBalance> findOne(PlatformBalance condition) {
        List<PlatformBalance> results = findBy(condition);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single platform balance but found " + results.size());
        }
        return Optional.of(results.get(0));
    }
}
