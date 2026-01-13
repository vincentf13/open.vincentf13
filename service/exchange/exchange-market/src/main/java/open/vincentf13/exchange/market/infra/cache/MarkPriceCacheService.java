package open.vincentf13.exchange.market.infra.cache;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import open.vincentf13.exchange.market.domain.model.MarkPriceSnapshot;
import open.vincentf13.exchange.market.infra.MarketEvent;
import open.vincentf13.exchange.market.infra.messaging.publisher.MarkPriceEventPublisher;
import open.vincentf13.exchange.market.infra.persistence.repository.MarkPriceSnapshotRepository;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkPriceCacheService {

  private static final Duration SNAPSHOT_INTERVAL = Duration.ofSeconds(5);
  private static final int CHANGE_RATE_SCALE = 8;

  private final Map<Long, MarkPriceSnapshot> cache = new ConcurrentHashMap<>();
  private final MarkPriceSnapshotRepository repository;
  private final MarkPriceEventPublisher eventPublisher;

  public MarkPriceCacheService(
      MarkPriceSnapshotRepository repository, MarkPriceEventPublisher eventPublisher) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
  }

  public void reset() {
    cache.clear();
  }

  public Optional<MarkPriceSnapshot> getLatest(Long instrumentId) {
    if (instrumentId == null) {
      return Optional.empty();
    }
    MarkPriceSnapshot snapshot = cache.get(instrumentId);
    if (snapshot != null) {
      return Optional.of(snapshot);
    }
    Optional<MarkPriceSnapshot> latest =
        repository
            .findLatest(instrumentId)
            .map(
                found -> {
                  cache.put(instrumentId, found);
                  return found;
                });
    if (latest.isPresent()) {
      return latest;
    }
    MarkPriceSnapshot defaultSnapshot = createDefaultSnapshot(instrumentId);
    MarkPriceSnapshot persisted = repository.insertSelective(defaultSnapshot);
    cache.put(instrumentId, persisted);
    return Optional.of(persisted);
  }

  private MarkPriceSnapshot createDefaultSnapshot(Long instrumentId) {
    Instant now = Instant.now();
    return MarkPriceSnapshot.builder()
        .instrumentId(instrumentId)
        .markPrice(BigDecimal.ONE)
        .markPriceChangeRate(BigDecimal.ONE)
        .tradeId(0L)
        .tradeExecutedAt(now)
        .calculatedAt(now)
        .build();
  }

  @Transactional(rollbackFor = Exception.class)
  public void record(
      Long instrumentId, Long tradeId, BigDecimal markPrice, Instant tradeExecutedAt) {
    if (instrumentId == null || tradeId == null || markPrice == null || tradeExecutedAt == null) {
      return;
    }
    Instant calculatedAt = Instant.now();
    MarkPriceSnapshot previous = cache.get(instrumentId);
    BigDecimal changeRate = BigDecimal.ONE;
    if (previous != null
        && previous.getMarkPrice() != null
        && previous.getMarkPrice().compareTo(BigDecimal.ZERO) > 0) {
      changeRate =
          markPrice.divide(previous.getMarkPrice(), CHANGE_RATE_SCALE, RoundingMode.HALF_UP);
    }
    MarkPriceSnapshot current =
        MarkPriceSnapshot.builder()
            .instrumentId(instrumentId)
            .markPrice(markPrice)
            .markPriceChangeRate(changeRate)
            .tradeId(tradeId)
            .tradeExecutedAt(tradeExecutedAt)
            .calculatedAt(calculatedAt)
            .build();

    boolean priceChanged =
        previous == null
            || previous.getMarkPrice() == null
            || markPrice.compareTo(previous.getMarkPrice()) != 0;
    boolean intervalExceeded =
        previous == null
            || previous.getCalculatedAt() == null
            || Duration.between(previous.getCalculatedAt(), calculatedAt)
                    .compareTo(SNAPSHOT_INTERVAL)
                >= 0;

    cache.put(instrumentId, current);

    if (!(priceChanged || intervalExceeded)) {
      return;
    }

    MarkPriceSnapshot persisted = repository.insertSelective(current);
    cache.put(instrumentId, persisted);
    eventPublisher.publishMarkPriceUpdated(persisted);
    OpenLog.debug(
        MarketEvent.MARK_PRICE_CACHE_UPDATED, "instrumentId", instrumentId, "tradeId", tradeId);
  }
}
