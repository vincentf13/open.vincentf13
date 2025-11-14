package open.vincentf13.exchange.risk.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.RiskSnapshot;
import open.vincentf13.exchange.risk.infra.persistence.mapper.RiskSnapshotMapper;
import open.vincentf13.exchange.risk.infra.persistence.po.RiskSnapshotPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RiskSnapshotRepositoryImpl implements RiskSnapshotRepository {

    private final RiskSnapshotMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public Optional<RiskSnapshot> findByUserAndInstrument(Long userId, Long instrumentId) {
        RiskSnapshotPO po = mapper.findByUserAndInstrument(userId, instrumentId);
        return Optional.ofNullable(OpenMapstruct.map(po, RiskSnapshot.class));
    }

    @Override
    public RiskSnapshot save(RiskSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        RiskSnapshotPO po = OpenMapstruct.map(snapshot, RiskSnapshotPO.class);
        Instant now = Instant.now();
        po.setUpdatedAt(now);
        if (po.getSnapshotId() == null) {
            po.setSnapshotId(idGenerator.newLong());
            po.setCreatedAt(now);
            mapper.insert(po);
        } else {
            mapper.update(po);
        }
        return OpenMapstruct.map(po, RiskSnapshot.class);
    }
}
