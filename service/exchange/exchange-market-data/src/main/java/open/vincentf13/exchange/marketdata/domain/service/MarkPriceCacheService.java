package open.vincentf13.exchange.marketdata.domain.service;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.marketdata.domain.model.MarkPriceSnapshot;
import open.vincentf13.exchange.marketdata.infra.messaging.publisher.MarkPriceEventPublisher;
import open.vincentf13.exchange.marketdata.infra.persistence.repository.MarkPriceSnapshotRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class MarkPriceCacheService {

    private static final Duration SNAPSHOT_INTERVAL = Duration.ofSeconds(5);

    private final Map<Long, MarkPriceSnapshot> cache = new ConcurrentHashMap<>();
    private final MarkPriceSnapshotRepository repository;
    private final MarkPriceEventPublisher eventPublisher;

    public MarkPriceCacheService(MarkPriceSnapshotRepository repository,
                                 MarkPriceEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public Optional<MarkPriceSnapshot> getLatest(Long instrumentId) {
        if (instrumentId == null) {
            return Optional.empty();
        }
        MarkPriceSnapshot snapshot = cache.get(instrumentId);
        if (snapshot != null) {
            return Optional.of(snapshot);
        }
        Optional<MarkPriceSnapshot> latest = repository.findLatest(instrumentId)
                .map(found -> {
                    cache.put(instrumentId, found);
                    return found;
                });
        if (latest.isPresent()) {
            return latest;
        }
        MarkPriceSnapshot defaultSnapshot = createDefaultSnapshot(instrumentId);
        MarkPriceSnapshot persisted = repository.save(defaultSnapshot);
        cache.put(instrumentId, persisted);
        return Optional.of(persisted);
    }

    private MarkPriceSnapshot createDefaultSnapshot(Long instrumentId) {
        Instant now = Instant.now();
        return MarkPriceSnapshot.builder()
                .instrumentId(instrumentId)
                .markPrice(BigDecimal.ZERO)
                .tradeId(0L)
                .tradeExecutedAt(now)
                .calculatedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void record(Long instrumentId, Long tradeId, BigDecimal markPrice, Instant tradeExecutedAt) {
        if (instrumentId == null || tradeId == null || markPrice == null || tradeExecutedAt == null) {
            return;
        }
        Instant calculatedAt = Instant.now();
        MarkPriceSnapshot previous = cache.get(instrumentId);
        MarkPriceSnapshot current = MarkPriceSnapshot.builder()
                .instrumentId(instrumentId)
                .markPrice(markPrice)
                .tradeId(tradeId)
                .tradeExecutedAt(tradeExecutedAt)
                .calculatedAt(calculatedAt)
                .build();

        boolean priceChanged = previous == null
                || previous.getMarkPrice() == null
                || markPrice.compareTo(previous.getMarkPrice()) != 0;
        boolean intervalExceeded = previous == null
                || previous.getCalculatedAt() == null
                || Duration.between(previous.getCalculatedAt(), calculatedAt).compareTo(SNAPSHOT_INTERVAL) >= 0;

        cache.put(instrumentId, current);

        if (!(priceChanged || intervalExceeded)) {
            return;
        }

        MarkPriceSnapshot persisted = repository.save(current);
        cache.put(instrumentId, persisted);
        eventPublisher.publishMarkPriceUpdated(persisted);
        log.debug("Mark price updated for instrument {} with trade {}", instrumentId, tradeId);
    }
}
