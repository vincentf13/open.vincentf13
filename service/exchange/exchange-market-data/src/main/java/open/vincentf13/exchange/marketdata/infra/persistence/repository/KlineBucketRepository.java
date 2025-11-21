package open.vincentf13.exchange.marketdata.infra.persistence.repository;

import open.vincentf13.exchange.marketdata.domain.model.KlineBucket;

import java.time.Instant;
import java.util.Optional;

public interface KlineBucketRepository {

    Optional<KlineBucket> findActive(Long instrumentId, String period, Instant targetTime);

    Optional<KlineBucket> findByStart(Long instrumentId, String period, Instant bucketStart);

    KlineBucket save(KlineBucket bucket);
}
