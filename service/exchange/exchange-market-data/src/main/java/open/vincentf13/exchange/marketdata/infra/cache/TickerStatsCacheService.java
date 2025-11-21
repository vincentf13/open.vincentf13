package open.vincentf13.exchange.marketdata.infra.cache;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.marketdata.domain.model.TickerStats;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TickerStatsCacheService {

    private final Map<Long, TickerStats> cache = new ConcurrentHashMap<>();
    private final MarkPriceCacheService markPriceCacheService;
    private final KlineAggregationService klineAggregationService;

    public TickerStatsCacheService(MarkPriceCacheService markPriceCacheService,
                                   KlineAggregationService klineAggregationService) {
        this.markPriceCacheService = markPriceCacheService;
        this.klineAggregationService = klineAggregationService;
    }

    public void recordTrade(Long instrumentId,
                            Long tradeId,
                            BigDecimal price,
                            BigDecimal quantity,
                            Instant executedAt) {
        if (instrumentId == null || price == null || quantity == null) {
            return;
        }
        TickerStats current = cache.get(instrumentId);
        if (current == null) {
            current = TickerStats.builder()
                    .instrumentId(instrumentId)
                    .lastPrice(price)
                    .volume24h(quantity)
                    .turnover24h(price.multiply(quantity))
                    .high24h(price)
                    .low24h(price)
                    .open24h(price)
                    .priceChange24h(BigDecimal.ZERO)
                    .priceChangePct(BigDecimal.ZERO)
                    .updatedAt(executedAt)
                    .build();
            cache.put(instrumentId, current);
            return;
        }

        current.setLastPrice(price);
        current.setVolume24h(current.getVolume24h().add(quantity));
        current.setTurnover24h(current.getTurnover24h().add(price.multiply(quantity)));
        current.setHigh24h(price.max(current.getHigh24h()));
        current.setLow24h(price.min(current.getLow24h()));
        if (current.getOpen24h() == null) {
            current.setOpen24h(price);
        }
        BigDecimal priceChange = price.subtract(current.getOpen24h());
        current.setPriceChange24h(priceChange);
        if (current.getOpen24h().compareTo(BigDecimal.ZERO) > 0) {
            current.setPriceChangePct(priceChange.divide(current.getOpen24h(), 8, java.math.RoundingMode.HALF_UP));
        }
        current.setUpdatedAt(executedAt);
        cache.put(instrumentId, current);

        markPriceCacheService.record(instrumentId, tradeId, price, executedAt);
        klineAggregationService.recordTrade(instrumentId, price, quantity, executedAt, null);
    }

    public TickerStats get(Long instrumentId) {
        return cache.get(instrumentId);
    }
}
