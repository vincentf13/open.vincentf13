package open.vincentf13.exchange.marketdata.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.marketdata.domain.model.KlineBucket;
import open.vincentf13.exchange.marketdata.infra.persistence.mapper.KlineBucketMapper;
import open.vincentf13.exchange.marketdata.infra.persistence.po.KlineBucketPO;
import open.vincentf13.sdk.core.OpenObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class KlineBucketRepository {

    private final KlineBucketMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public Optional<KlineBucket> findByStart(@NotNull Long instrumentId, @NotBlank String period, @NotNull Instant bucketStart) {
        return Optional.ofNullable(mapper.findByInstrumentPeriodAndStart(instrumentId, period, bucketStart))
                .map(po -> OpenObjectMapper.convert(po, KlineBucket.class));
    }

    public KlineBucket insertSelective(@NotNull @Valid KlineBucket bucket) {
        KlineBucketPO record = OpenObjectMapper.convert(bucket, KlineBucketPO.class);
        if (record.getClosed() == null) {
            record.setClosed(Boolean.FALSE);
        }
        if (record.getBucketId() == null) {
            record.setBucketId(idGenerator.newLong());
        }
        mapper.insertSelective(record);
        bucket.setBucketId(record.getBucketId());
        bucket.setClosed(record.getClosed());
        return bucket;
    }

    public boolean updateSelectiveBy(@NotNull @Valid KlineBucket update,
                                     @NotNull Long bucketId,
                                     Long instrumentId,
                                     String period,
                                     Boolean closed) {
        KlineBucketPO record = OpenObjectMapper.convert(update, KlineBucketPO.class);
        return mapper.updateSelectiveBy(record, bucketId, instrumentId, period, closed) > 0;
    }

    public List<KlineBucket> findBetween(@NotNull Long instrumentId, @NotBlank String period, @NotNull Instant start, @NotNull Instant end) {
        List<KlineBucketPO> records = mapper.findBucketsBetween(instrumentId, period, start, end);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(po -> OpenObjectMapper.convert(po, KlineBucket.class))
                .toList();
    }

    public Optional<KlineBucket> findLatestBefore(@NotNull Long instrumentId, @NotBlank String period, @NotNull Instant start) {
        return Optional.ofNullable(mapper.findLatestBefore(instrumentId, period, start))
                .map(po -> OpenObjectMapper.convert(po, KlineBucket.class));
    }
}
