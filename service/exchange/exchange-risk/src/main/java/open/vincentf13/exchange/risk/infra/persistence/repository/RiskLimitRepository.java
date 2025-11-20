package open.vincentf13.exchange.risk.infra.persistence.repository;

import open.vincentf13.exchange.risk.domain.model.RiskLimit;

import java.util.Optional;

public interface RiskLimitRepository {

    Optional<RiskLimit> findEffective(Long instrumentId);

    RiskLimit save(RiskLimit riskLimit);
}
