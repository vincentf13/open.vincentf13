package open.vincentf13.exchange.marketdata.infra.persistence.repository;

import open.vincentf13.exchange.marketdata.domain.model.KlineBucket;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KlineBucketRepository {

    Optional<KlineBucket> findActive(Long instrumentId, String period, Instant targetTime);

    Optional<KlineBucket> findByStart(Long instrumentId, String period, Instant bucketStart);

    KlineBucket save(KlineBucket bucket);

    List<KlineBucket> findRecent(Long instrumentId, String period, int limit);

    List<KlineBucket> findBetween(Long instrumentId, String period, Instant start, Instant end);

    Optional<KlineBucket> findLatestBefore(Long instrumentId, String period, Instant start);
}
