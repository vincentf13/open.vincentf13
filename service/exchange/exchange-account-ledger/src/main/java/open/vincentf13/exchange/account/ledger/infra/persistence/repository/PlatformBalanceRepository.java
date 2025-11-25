package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformBalance;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.PlatformBalanceMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformBalancePO;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.sdk.common.enums.AssetSymbol;
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
public class PlatformBalanceRepository {

    private final PlatformBalanceMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public PlatformBalance insertSelective(@NotNull @Valid PlatformBalance platformBalance) {
        if (platformBalance.getId() == null) {
            platformBalance.setId(idGenerator.newLong());
        }
        if (platformBalance.getVersion() == null) {
            platformBalance.setVersion(0);
        }
        PlatformBalancePO po = OpenObjectMapper.convert(platformBalance, PlatformBalancePO.class);
        mapper.insertSelective(po);
        return platformBalance;
    }

    public boolean updateSelectiveBy(@NotNull @Valid PlatformBalance platformBalance, @NotNull Long id, Integer expectedVersion) {
        PlatformBalancePO po = OpenObjectMapper.convert(platformBalance, PlatformBalancePO.class);
        return mapper.updateSelectiveBy(po, id, expectedVersion) > 0;
    }

    public List<PlatformBalance> findBy(@NotNull PlatformBalance condition) {
        PlatformBalancePO probe = OpenObjectMapper.convert(condition, PlatformBalancePO.class);
        return mapper.findBy(probe).stream()
                .map(item -> OpenObjectMapper.convert(item, PlatformBalance.class))
                .collect(Collectors.toList());
    }

    public Optional<PlatformBalance> findOne(@NotNull PlatformBalance condition) {
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
                        return insertSelective(PlatformBalance.createDefault(accountId, accountCode, asset));
                    } catch (DuplicateKeyException ex) {
                        return findOne(probe)
                                .orElseThrow(() -> ex);
                    }
                });
    }
}
