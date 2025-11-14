package open.vincentf13.exchange.risk.infra.persistence.repository;

import open.vincentf13.exchange.risk.domain.model.RiskSnapshot;

import java.util.Optional;

public interface RiskSnapshotRepository {

    Optional<RiskSnapshot> findByUserAndInstrument(Long userId, Long instrumentId);

    RiskSnapshot save(RiskSnapshot snapshot);
}
