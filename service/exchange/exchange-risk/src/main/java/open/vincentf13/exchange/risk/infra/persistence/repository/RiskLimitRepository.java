package open.vincentf13.exchange.risk.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.infra.persistence.mapper.RiskLimitMapper;
import open.vincentf13.exchange.risk.infra.persistence.po.RiskLimitPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class RiskLimitRepository {

    private final RiskLimitMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public Optional<RiskLimit> findEffective(@NotNull Long instrumentId) {
        RiskLimitPO po = mapper.findEffective(instrumentId);
        return Optional.ofNullable(OpenMapstruct.map(po, RiskLimit.class));
    }

    public RiskLimit save(@NotNull @Valid RiskLimit riskLimit) {
        RiskLimitPO po = OpenMapstruct.map(riskLimit, RiskLimitPO.class);
        if (po.getRiskLimitId() == null) {
            po.setRiskLimitId(idGenerator.newLong());
            mapper.insertSelective(po);
        } else {
            mapper.updateSelective(po);
        }
        return OpenMapstruct.map(po, RiskLimit.class);
    }
}
