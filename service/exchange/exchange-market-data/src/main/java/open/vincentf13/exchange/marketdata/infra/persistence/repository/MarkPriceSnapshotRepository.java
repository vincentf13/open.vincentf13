package open.vincentf13.exchange.marketdata.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.marketdata.domain.model.MarkPriceSnapshot;
import open.vincentf13.exchange.marketdata.infra.persistence.mapper.MarkPriceSnapshotMapper;
import open.vincentf13.exchange.marketdata.infra.persistence.po.MarkPriceSnapshotPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class MarkPriceSnapshotRepository {

    private final MarkPriceSnapshotMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public MarkPriceSnapshot insertSelective(@NotNull @Valid MarkPriceSnapshot snapshot) {
        MarkPriceSnapshotPO record = OpenMapstruct.map(snapshot, MarkPriceSnapshotPO.class);
        if (record.getSnapshotId() == null) {
            record.setSnapshotId(idGenerator.newLong());
        }
        if (record.getCalculatedAt() == null) {
            record.setCalculatedAt(Instant.now());
        }
        mapper.insertSelective(record);
        snapshot.setSnapshotId(record.getSnapshotId());
        snapshot.setCalculatedAt(record.getCalculatedAt());
        return snapshot;
    }

    public Optional<MarkPriceSnapshot> findLatest(@NotNull Long instrumentId) {
        return Optional.ofNullable(mapper.findLatest(instrumentId))
                .map(po -> OpenMapstruct.map(po, MarkPriceSnapshot.class));
    }
}
