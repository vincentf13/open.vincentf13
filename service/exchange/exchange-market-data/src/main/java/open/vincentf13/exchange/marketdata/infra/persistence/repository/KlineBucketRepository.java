package open.vincentf13.exchange.marketdata.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.marketdata.domain.model.KlineBucket;
import open.vincentf13.exchange.marketdata.infra.persistence.mapper.KlineBucketMapper;
import open.vincentf13.exchange.marketdata.infra.persistence.po.KlineBucketPO;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Validated
public class KlineBucketRepository {

    private final KlineBucketMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public Optional<KlineBucket> findActive(@NotNull Long instrumentId, @NotBlank String period, @NotNull Instant targetTime) {
        return Optional.ofNullable(mapper.findActiveBucket(instrumentId, period, targetTime))
                .map(po -> OpenMapstruct.map(po, KlineBucket.class));
    }

    public Optional<KlineBucket> findByStart(@NotNull Long instrumentId, @NotBlank String period, @NotNull Instant bucketStart) {
        return Optional.ofNullable(mapper.findByInstrumentPeriodAndStart(instrumentId, period, bucketStart))
                .map(po -> OpenMapstruct.map(po, KlineBucket.class));
    }

    public KlineBucket save(@NotNull @Valid KlineBucket bucket) {
        Instant now = Instant.now();
        KlineBucketPO record = OpenMapstruct.map(bucket, KlineBucketPO.class);
        if (record.getClosed() == null) {
            record.setClosed(Boolean.FALSE);
        }
        if (record.getBucketId() == null) {
            record.setBucketId(idGenerator.newLong());
            mapper.insertSelective(record);
        } else {
            mapper.updateById(record);
        }
        return OpenMapstruct.map(record, KlineBucket.class);
    }

    public List<KlineBucket> findRecent(@NotNull Long instrumentId, @NotBlank String period, @Min(1) int limit) {
        List<KlineBucketPO> records = mapper.findRecentBuckets(instrumentId, period, limit);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(po -> OpenMapstruct.map(po, KlineBucket.class))
                .toList();
    }

    public List<KlineBucket> findBetween(@NotNull Long instrumentId, @NotBlank String period, @NotNull Instant start, @NotNull Instant end) {
        List<KlineBucketPO> records = mapper.findBucketsBetween(instrumentId, period, start, end);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(po -> OpenMapstruct.map(po, KlineBucket.class))
                .toList();
    }

    public Optional<KlineBucket> findLatestBefore(@NotNull Long instrumentId, @NotBlank String period, @NotNull Instant start) {
        return Optional.ofNullable(mapper.findLatestBefore(instrumentId, period, start))
                .map(po -> OpenMapstruct.map(po, KlineBucket.class));
    }
}
