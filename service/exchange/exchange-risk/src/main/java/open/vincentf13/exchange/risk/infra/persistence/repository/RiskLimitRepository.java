package open.vincentf13.exchange.risk.infra.persistence.repository;

import open.vincentf13.exchange.risk.domain.model.RiskLimit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RiskLimitRepository {

    Optional<RiskLimit> findEffective(Long instrumentId, Integer tier, Instant asOf);

    List<RiskLimit> findActiveByInstrument(Long instrumentId, Instant asOf);

    RiskLimit save(RiskLimit riskLimit);
}
