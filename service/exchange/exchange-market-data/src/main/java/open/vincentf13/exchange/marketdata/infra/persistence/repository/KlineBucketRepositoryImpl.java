package open.vincentf13.exchange.marketdata.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.marketdata.domain.model.KlineBucket;
import open.vincentf13.exchange.marketdata.infra.persistence.mapper.KlineBucketMapper;
import open.vincentf13.exchange.marketdata.infra.persistence.po.KlineBucketPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class KlineBucketRepositoryImpl implements KlineBucketRepository {

    private final KlineBucketMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public Optional<KlineBucket> findActive(Long instrumentId, String period, Instant targetTime) {
        if (instrumentId == null || period == null || targetTime == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findActiveBucket(instrumentId, period, targetTime))
                .map(po -> OpenMapstruct.map(po, KlineBucket.class));
    }

    @Override
    public Optional<KlineBucket> findByStart(Long instrumentId, String period, Instant bucketStart) {
        if (instrumentId == null || period == null || bucketStart == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findByInstrumentPeriodAndStart(instrumentId, period, bucketStart))
                .map(po -> OpenMapstruct.map(po, KlineBucket.class));
    }

    @Override
    public KlineBucket save(KlineBucket bucket) {
        if (bucket == null) {
            throw new IllegalArgumentException("bucket must not be null");
        }
        Instant now = Instant.now();
        KlineBucketPO record = OpenMapstruct.map(bucket, KlineBucketPO.class);
        if (record.getClosed() == null) {
            record.setClosed(Boolean.FALSE);
        }
        if (record.getBucketId() == null) {
            record.setBucketId(idGenerator.newLong());
            if (record.getCreatedAt() == null) {
                record.setCreatedAt(now);
            }
            if (record.getUpdatedAt() == null) {
                record.setUpdatedAt(now);
            }
            mapper.insertSelective(record);
        } else {
            record.setUpdatedAt(now);
            mapper.updateById(record);
        }
        return OpenMapstruct.map(record, KlineBucket.class);
    }
}
