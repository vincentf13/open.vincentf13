package open.vincentf13.exchange.risk.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.RiskSnapshot;
import open.vincentf13.exchange.risk.infra.persistence.mapper.RiskSnapshotMapper;
import open.vincentf13.exchange.risk.infra.persistence.po.RiskSnapshotPO;
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
public class RiskSnapshotRepository {

    private final RiskSnapshotMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public Optional<RiskSnapshot> findByUserAndInstrument(@NotNull Long userId, @NotNull Long instrumentId) {
        RiskSnapshotPO condition = RiskSnapshotPO.builder()
                .userId(userId)
                .instrumentId(instrumentId)
                .build();
        return mapper.findBy(condition).stream()
                .findFirst()
                .map(po -> OpenMapstruct.map(po, RiskSnapshot.class));
    }

    public RiskSnapshot save(@NotNull @Valid RiskSnapshot snapshot) {
        RiskSnapshotPO po = OpenMapstruct.map(snapshot, RiskSnapshotPO.class);
        if (po.getSnapshotId() == null) {
            po.setSnapshotId(idGenerator.newLong());
            mapper.insertSelective(po);
        } else {
            mapper.updateStatusByIdAndVersion(po.getSnapshotId(), po.getStatus(), po.getRiskVersion());
        }
        return OpenMapstruct.map(po, RiskSnapshot.class);
    }
}
