package open.vincentf13.exchange.market.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.sdk.rest.api.dto.KlineResponse;
import open.vincentf13.exchange.market.sdk.rest.api.enums.KlinePeriod;
import open.vincentf13.exchange.market.domain.model.KlineBucket;
import open.vincentf13.exchange.market.infra.persistence.po.KlineBucketPO;
import open.vincentf13.exchange.market.infra.persistence.repository.KlineBucketRepository;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KlineQueryService {
    
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;
    
    private final KlineBucketRepository klineBucketRepository;
    
    public List<KlineResponse> getKlines(Long instrumentId,
                                         String periodValue,
                                         Integer limit) {
        if (instrumentId == null || periodValue == null) {
            return List.of();
        }
        String normalizedPeriod = periodValue.trim();
        if (normalizedPeriod.isEmpty()) {
            return List.of();
        }
        KlinePeriod period = KlinePeriod.fromValue(normalizedPeriod);
        int fetchSize = resolveLimit(limit);
        Duration duration = period.getDuration();
        Instant latestStart = alignBucketStart(Instant.now(), duration);
        Instant earliestStart = calculateEarliestStart(latestStart, duration, fetchSize);
        
        List<KlineBucket> existing = klineBucketRepository.findBy(Wrappers.<KlineBucketPO>lambdaQuery()
                                                                          .eq(KlineBucketPO::getInstrumentId, instrumentId)
                                                                          .eq(KlineBucketPO::getPeriod, period.getValue())
                                                                          .between(KlineBucketPO::getBucketStart, earliestStart, latestStart)
                                                                          .orderByAsc(KlineBucketPO::getBucketStart));
        Map<Instant, KlineBucket> bucketByStart = new HashMap<>();
        for (KlineBucket bucket : existing) {
            bucketByStart.put(bucket.getBucketStart(), bucket);
        }
        
        BigDecimal previousClose = klineBucketRepository.findOne(Wrappers.<KlineBucketPO>lambdaQuery()
                                                                         .eq(KlineBucketPO::getInstrumentId, instrumentId)
                                                                         .eq(KlineBucketPO::getPeriod, period.getValue())
                                                                         .lt(KlineBucketPO::getBucketStart, earliestStart)
                                                                         .orderByDesc(KlineBucketPO::getBucketStart)
                                                                         .last("LIMIT 1"))
                                                        .map(KlineBucket::getClosePrice)
                                                        .orElse(null);
        
        List<KlineBucket> normalized = new ArrayList<>(fetchSize);
        Instant cursor = earliestStart;
        for (int i = 0; i < fetchSize; i++) {
            KlineBucket bucket = bucketByStart.get(cursor);
            if (bucket == null) {
                bucket = createSyntheticBucket(instrumentId, period, cursor, duration, previousClose);
            }
            normalized.add(bucket);
            if (bucket.getClosePrice() != null) {
                previousClose = bucket.getClosePrice();
            }
            cursor = cursor.plus(duration);
        }
        return OpenObjectMapper.convertList(normalized, KlineResponse.class);
    }
    
    private int resolveLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_LIMIT : limit;
        if (resolved <= 0) {
            resolved = DEFAULT_LIMIT;
        }
        return Math.min(resolved, MAX_LIMIT);
    }
    
    private Instant calculateEarliestStart(Instant latestStart,
                                           Duration duration,
                                           int fetchSize) {
        if (fetchSize <= 1) {
            return latestStart;
        }
        long offsetSeconds = duration.getSeconds() * (fetchSize - 1L);
        return latestStart.minusSeconds(offsetSeconds);
    }
    
    private Instant alignBucketStart(Instant source,
                                     Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds <= 0) {
            return source;
        }
        long epoch = source.getEpochSecond();
        long start = (epoch / seconds) * seconds;
        return Instant.ofEpochSecond(start);
    }
    
    private KlineBucket createSyntheticBucket(Long instrumentId,
                                              KlinePeriod period,
                                              Instant bucketStart,
                                              Duration duration,
                                              BigDecimal previousClose) {
        BigDecimal price = previousClose == null ? BigDecimal.ZERO : previousClose;
        Instant bucketEnd = bucketStart.plus(duration);
        return KlineBucket.builder()
                          .instrumentId(instrumentId)
                          .period(period.getValue())
                          .bucketStart(bucketStart)
                          .bucketEnd(bucketEnd)
                          .openPrice(price)
                          .highPrice(price)
                          .lowPrice(price)
                          .closePrice(price)
                          .volume(BigDecimal.ZERO)
                          .turnover(BigDecimal.ZERO)
                          .tradeCount(0)
                          .takerBuyVolume(BigDecimal.ZERO)
                          .takerBuyTurnover(BigDecimal.ZERO)
                          .isClosed(Boolean.TRUE)
                          .build();
    }
}
