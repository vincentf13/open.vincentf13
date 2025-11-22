package open.vincentf13.exchange.account.ledger.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.ledger.infra.persistence.mapper.PlatformAccountMapper;
import open.vincentf13.exchange.account.ledger.infra.persistence.po.PlatformAccountPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PlatformAccountRepositoryImpl implements PlatformAccountRepository {

    private final PlatformAccountMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public PlatformAccount insert(PlatformAccount platformAccount) {
        if (platformAccount.getAccountId() == null) {
            platformAccount.setAccountId(idGenerator.newLong());
        }
        if (platformAccount.getCreatedAt() == null) {
            platformAccount.setCreatedAt(Instant.now());
        }
        PlatformAccountPO po = OpenMapstruct.map(platformAccount, PlatformAccountPO.class);
        mapper.insertSelective(po);
        if (po.getCreatedAt() != null) {
            platformAccount.setCreatedAt(po.getCreatedAt());
        }
        return platformAccount;
    }

    @Override
    public List<PlatformAccount> findBy(PlatformAccount condition) {
        PlatformAccountPO probe = OpenMapstruct.map(condition, PlatformAccountPO.class);
        return mapper.findBy(probe).stream()
                .map(item -> OpenMapstruct.map(item, PlatformAccount.class))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PlatformAccount> findOne(PlatformAccount condition) {
        List<PlatformAccount> results = findBy(condition);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single platform account but found " + results.size());
        }
        return Optional.of(results.get(0));
    }
}
