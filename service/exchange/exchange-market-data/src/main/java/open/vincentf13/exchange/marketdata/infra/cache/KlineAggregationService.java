package open.vincentf13.exchange.marketdata.infra.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.marketdata.domain.model.KlineBucket;
import open.vincentf13.exchange.market.sdk.rest.api.enums.KlinePeriod;
import open.vincentf13.exchange.marketdata.infra.persistence.repository.KlineBucketRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class KlineAggregationService {

    private static final Set<KlinePeriod> SUPPORTED_PERIODS = EnumSet.of(
            KlinePeriod.ONE_MINUTE,
            KlinePeriod.FIVE_MINUTES,
            KlinePeriod.ONE_HOUR,
            KlinePeriod.ONE_DAY
    );

    private final KlineBucketRepository klineBucketRepository;
    private final Map<KlineKey, KlineBucket> activeBuckets = new ConcurrentHashMap<>();

    public void recordTrade(Long instrumentId,
                             BigDecimal price,
                             BigDecimal quantity,
                             Instant executedAt,
                             Boolean takerIsBuyer) {
        if (instrumentId == null || price == null || quantity == null || executedAt == null) {
            return;
        }
        for (KlinePeriod period : SUPPORTED_PERIODS) {
            Instant bucketStart = alignBucketStart(executedAt, period.getDuration());
            KlineBucket bucket = getOrLoadBucket(instrumentId, period, bucketStart);
            applyTrade(bucket, price, quantity, executedAt, takerIsBuyer);
        }
    }

    public void closeExpiredBuckets(Instant now) {
        if (now == null) {
            now = Instant.now();
        }
        for (Map.Entry<KlineKey, KlineBucket> entry : activeBuckets.entrySet()) {
            KlineBucket bucket = entry.getValue();
            if (bucket == null || bucket.getBucketEnd() == null) {
                continue;
            }
            if (!now.isAfter(bucket.getBucketEnd())) {
                continue;
            }
            finalizeBucket(entry.getKey(), bucket);
        }
    }

    private void finalizeBucket(KlineKey key, KlineBucket bucket) {
        KlinePeriod period = KlinePeriod.fromValue(key.period());
        boolean emptyBucket = bucket.getTradeCount() == null || bucket.getTradeCount() == 0;
        if (emptyBucket) {
            BigDecimal prevClose = findPreviousClose(bucket, period);
            bucket.setOpenPrice(prevClose);
            bucket.setHighPrice(prevClose);
            bucket.setLowPrice(prevClose);
            bucket.setClosePrice(prevClose);
            bucket.setVolume(BigDecimal.ZERO);
            bucket.setTurnover(BigDecimal.ZERO);
            bucket.setTradeCount(0);
            bucket.setTakerBuyVolume(BigDecimal.ZERO);
            bucket.setTakerBuyTurnover(BigDecimal.ZERO);
        }
        bucket.setClosed(Boolean.TRUE);
        bucket.setUpdatedAt(Instant.now());
        klineBucketRepository.save(bucket);
    }

    private BigDecimal findPreviousClose(KlineBucket bucket, KlinePeriod period) {
        Instant prevStart = bucket.getBucketStart().minus(period.getDuration());
        return klineBucketRepository.findByStart(bucket.getInstrumentId(), period.getValue(), prevStart)
                .map(KlineBucket::getClosePrice)
                .filter(Objects::nonNull)
                .orElse(BigDecimal.ZERO);
    }

    private KlineBucket getOrLoadBucket(Long instrumentId, KlinePeriod period, Instant bucketStart) {
        Instant bucketEnd = bucketStart.plus(period.getDuration());
        KlineKey key = new KlineKey(instrumentId, period.getValue());
        // 對指定 key 進行一次「讀取目前值 → 根據邏輯產生新值 → 寫回 Map」的整體操作
        return activeBuckets.compute(key, (k, current) -> {
            if (current != null && bucketStart.equals(current.getBucketStart())) {
                return current;
            }
            Optional<KlineBucket> persisted = klineBucketRepository.findByStart(instrumentId, period.getValue(), bucketStart);
            return persisted.orElseGet(() -> createNewBucket(instrumentId, period, bucketStart, bucketEnd));
        });
    }

    private KlineBucket createNewBucket(Long instrumentId, KlinePeriod period, Instant bucketStart, Instant bucketEnd) {
        KlineBucket bucket = KlineBucket.builder()
                .instrumentId(instrumentId)
                .period(period.getValue())
                .bucketStart(bucketStart)
                .bucketEnd(bucketEnd)
                .openPrice(BigDecimal.ZERO)
                .highPrice(BigDecimal.ZERO)
                .lowPrice(BigDecimal.ZERO)
                .closePrice(BigDecimal.ZERO)
                .volume(BigDecimal.ZERO)
                .turnover(BigDecimal.ZERO)
                .tradeCount(0)
                .takerBuyVolume(BigDecimal.ZERO)
                .takerBuyTurnover(BigDecimal.ZERO)
                .closed(Boolean.FALSE)
                .build();
        return klineBucketRepository.save(bucket);
    }

    private void applyTrade(KlineBucket bucket,
                            BigDecimal price,
                            BigDecimal quantity,
                            Instant executedAt,
                            Boolean takerIsBuyer) {
        if (bucket.getOpenPrice() == null || bucket.getOpenPrice().compareTo(BigDecimal.ZERO) == 0) {
            bucket.setOpenPrice(price);
            bucket.setHighPrice(price);
            bucket.setLowPrice(price);
        }
        bucket.setClosePrice(price);
        bucket.setHighPrice(max(bucket.getHighPrice(), price));
        bucket.setLowPrice(min(bucket.getLowPrice(), price));
        bucket.setVolume(safeAdd(bucket.getVolume(), quantity));
        bucket.setTurnover(safeAdd(bucket.getTurnover(), price.multiply(quantity)));
        bucket.setTradeCount(safeInt(bucket.getTradeCount()) + 1);
        if (Boolean.TRUE.equals(takerIsBuyer)) {
            bucket.setTakerBuyVolume(safeAdd(bucket.getTakerBuyVolume(), quantity));
            bucket.setTakerBuyTurnover(safeAdd(bucket.getTakerBuyTurnover(), price.multiply(quantity)));
        }
        bucket.setClosed(Boolean.FALSE);
        klineBucketRepository.save(bucket);
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        return left.max(right);
    }

    private BigDecimal min(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        return left.min(right);
    }

    private BigDecimal safeAdd(BigDecimal base, BigDecimal delta) {
        BigDecimal left = base == null ? BigDecimal.ZERO : base;
        BigDecimal right = delta == null ? BigDecimal.ZERO : delta;
        return left.add(right);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Instant alignBucketStart(Instant source, Duration duration) {
        long seconds = duration.getSeconds();
        long bucketStartEpoch = (source.getEpochSecond() / seconds) * seconds;
        return Instant.ofEpochSecond(bucketStartEpoch);
    }

    private record KlineKey(Long instrumentId, String period) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof KlineKey other)) {
                return false;
            }
            return Objects.equals(instrumentId, other.instrumentId) && Objects.equals(period, other.period);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instrumentId, period);
        }
    }
}
