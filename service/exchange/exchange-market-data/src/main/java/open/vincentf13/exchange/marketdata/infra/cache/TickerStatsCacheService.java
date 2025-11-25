package open.vincentf13.exchange.marketdata.infra.cache;

import open.vincentf13.exchange.marketdata.domain.model.TickerStats;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
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
                    .build();
            cache.put(instrumentId, current);
            return;
        }
        // TODO 目前沒有任何 24 小時的滑動窗口或過期機制。所以這套統計並非真正的「最近 24 小時」資料，而是從服務啟動後一路累加，
        //  若要符合 24h 定義，需要額外資料結構（如環形緩衝、時間桶、定期扣除過期成交）或改用資料庫/分析系統重新計算。
        //  現狀下只能視為簡易的累積統計，供快速顯示，不具嚴格時間窗語意。

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
        cache.put(instrumentId, current);

        markPriceCacheService.record(instrumentId, tradeId, price, executedAt);
        klineAggregationService.recordTrade(instrumentId, price, quantity, executedAt, null);
    }

    public TickerStats get(Long instrumentId) {
        if (instrumentId == null) {
            return null;
        }
        return cache.computeIfAbsent(instrumentId, this::createDefault);
    }

    private TickerStats createDefault(Long instrumentId) {
        Instant now = Instant.now();
        return TickerStats.builder()
                .instrumentId(instrumentId)
                .lastPrice(BigDecimal.ZERO)
                .volume24h(BigDecimal.ZERO)
                .turnover24h(BigDecimal.ZERO)
                .high24h(BigDecimal.ZERO)
                .low24h(BigDecimal.ZERO)
                .open24h(BigDecimal.ZERO)
                .priceChange24h(BigDecimal.ZERO)
                .priceChangePct(BigDecimal.ZERO)
                .build();
    }
}
