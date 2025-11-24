package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.LedgerBalance;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.LedgerBalanceMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.LedgerBalancePO;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.AccountType;
import open.vincentf13.exchange.sdk.common.enums.AssetSymbol;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Validated
@RequiredArgsConstructor
public class LedgerBalanceRepository {

    private final LedgerBalanceMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public LedgerBalance insert(@NotNull @Valid LedgerBalance balance) {
        if (balance.getId() == null) {
            balance.setId(idGenerator.newLong());
        }
        if (balance.getAccountId() == null) {
            balance.setAccountId(idGenerator.newLong());
        }
        if (balance.getCreatedAt() == null) {
            balance.setCreatedAt(Instant.now());
        }
        if (balance.getUpdatedAt() == null) {
            balance.setUpdatedAt(balance.getCreatedAt());
        }
        LedgerBalancePO po = OpenMapstruct.map(balance, LedgerBalancePO.class);
        if (balance.getAccountType() != null && po.getAccountType() == null) {
            po.setAccountType(balance.getAccountType().value());
        }
        if (balance.getAsset() != null && po.getAsset() == null) {
            po.setAsset(balance.getAsset().code());
        }
        mapper.insertSelective(po);
        if (po.getCreatedAt() != null) {
            balance.setCreatedAt(po.getCreatedAt());
        }
        if (po.getUpdatedAt() != null) {
            balance.setUpdatedAt(po.getUpdatedAt());
        }
        return balance;
    }

    public boolean updateWithVersion(@NotNull @Valid LedgerBalance balance, Integer expectedVersion) {
        balance.setUpdatedAt(Instant.now());
        LedgerBalancePO po = OpenMapstruct.map(balance, LedgerBalancePO.class);
        if (balance.getAccountType() != null && po.getAccountType() == null) {
            po.setAccountType(balance.getAccountType().value());
        }
        if (balance.getAsset() != null && po.getAsset() == null) {
            po.setAsset(balance.getAsset().code());
        }
        return mapper.updateByIdAndVersion(po, expectedVersion) > 0;
    }

    public List<LedgerBalance> findBy(@NotNull @Valid LedgerBalance condition) {
        LedgerBalancePO probe = OpenMapstruct.map(condition, LedgerBalancePO.class);
        if (condition.getAccountType() != null && probe.getAccountType() == null) {
            probe.setAccountType(condition.getAccountType().value());
        }
        if (condition.getAsset() != null && probe.getAsset() == null) {
            probe.setAsset(condition.getAsset().code());
        }
        return mapper.findBy(probe).stream()
                .map(item -> OpenMapstruct.map(item, LedgerBalance.class))
                .collect(Collectors.toList());
    }

    public Optional<LedgerBalance> findOne(@NotNull @Valid LedgerBalance condition) {
        List<LedgerBalance> results = findBy(condition);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single ledger balance but found " + results.size());
        }
        return Optional.of(results.get(0));
    }

    public LedgerBalance getOrCreate(@NotNull Long userId,
                                     @NotNull AccountType accountType,
                                     Long instrumentId,
                                     @NotNull AssetSymbol asset) {
        LedgerBalance probe = LedgerBalance.builder()
                .userId(userId)
                .accountType(accountType)
                .instrumentId(instrumentId)
                .asset(asset)
                .build();
        return findOne(probe)
                .orElseGet(() -> insert(LedgerBalance.createDefault(userId, accountType, instrumentId, asset)));
    }


}
