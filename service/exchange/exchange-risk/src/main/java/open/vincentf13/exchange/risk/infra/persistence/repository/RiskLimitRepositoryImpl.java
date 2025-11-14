package open.vincentf13.exchange.risk.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.infra.persistence.mapper.RiskLimitMapper;
import open.vincentf13.exchange.risk.infra.persistence.po.RiskLimitPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RiskLimitRepositoryImpl implements RiskLimitRepository {

    private final RiskLimitMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public Optional<RiskLimit> findEffective(Long instrumentId, Integer tier, Instant asOf) {
        RiskLimitPO po = mapper.findEffective(instrumentId, tier, asOf);
        return Optional.ofNullable(OpenMapstruct.map(po, RiskLimit.class));
    }

    @Override
    public List<RiskLimit> findActiveByInstrument(Long instrumentId, Instant asOf) {
        return OpenMapstruct.mapList(mapper.findActiveByInstrument(instrumentId, asOf), RiskLimit.class);
    }

    @Override
    public RiskLimit save(RiskLimit riskLimit) {
        if (riskLimit == null) {
            return null;
        }
        RiskLimitPO po = OpenMapstruct.map(riskLimit, RiskLimitPO.class);
        Instant now = Instant.now();
        po.setUpdatedAt(now);
        if (po.getRiskLimitId() == null) {
            po.setRiskLimitId(idGenerator.newLong());
            po.setCreatedAt(now);
            mapper.insert(po);
        } else {
            mapper.update(po);
        }
        return OpenMapstruct.map(po, RiskLimit.class);
    }
}
