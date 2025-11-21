package open.vincentf13.exchange.marketdata.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.marketdata.domain.model.MarkPriceSnapshot;
import open.vincentf13.exchange.marketdata.infra.persistence.mapper.MarkPriceSnapshotMapper;
import open.vincentf13.exchange.marketdata.infra.persistence.po.MarkPriceSnapshotPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MarkPriceSnapshotRepositoryImpl implements MarkPriceSnapshotRepository {

    private final MarkPriceSnapshotMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public MarkPriceSnapshot save(MarkPriceSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        MarkPriceSnapshotPO record = OpenMapstruct.map(snapshot, MarkPriceSnapshotPO.class);
        Instant calculatedAt = record.getCalculatedAt() != null ? record.getCalculatedAt() : Instant.now();
        record.setCalculatedAt(calculatedAt);
        if (record.getSnapshotId() == null) {
            record.setSnapshotId(idGenerator.newLong());
        }
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(calculatedAt);
        }
        if (record.getUpdatedAt() == null) {
            record.setUpdatedAt(calculatedAt);
        }
        mapper.insertSelective(record);
        return OpenMapstruct.map(record, MarkPriceSnapshot.class);
    }

    @Override
    public Optional<MarkPriceSnapshot> findLatest(Long instrumentId) {
        if (instrumentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findLatest(instrumentId))
                .map(po -> OpenMapstruct.map(po, MarkPriceSnapshot.class));
    }
}
