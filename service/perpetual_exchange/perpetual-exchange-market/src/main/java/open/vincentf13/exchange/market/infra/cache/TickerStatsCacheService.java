package open.vincentf13.exchange.market.infra.cache;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import open.vincentf13.exchange.market.domain.model.MarkPriceSnapshot;
import open.vincentf13.exchange.market.domain.model.TickerStats;
import org.springframework.stereotype.Service;

@Service
public class TickerStatsCacheService {

  private final Map<Long, TickerStats> cache = new ConcurrentHashMap<>();
  private final MarkPriceCacheService markPriceCacheService;
  private final KlineAggregationService klineAggregationService;
  private final InstrumentCache instrumentCache;

  public TickerStatsCacheService(
      MarkPriceCacheService markPriceCacheService,
      KlineAggregationService klineAggregationService,
      InstrumentCache instrumentCache) {
    this.markPriceCacheService = markPriceCacheService;
    this.klineAggregationService = klineAggregationService;
    this.instrumentCache = instrumentCache;
  }

  public void reset() {
    cache.clear();
  }

  public void recordTrade(
      Long instrumentId, Long tradeId, BigDecimal price, BigDecimal quantity, Instant executedAt) {
    if (instrumentId == null || price == null || quantity == null) {
      return;
    }
    BigDecimal contractMultiplier = requireContractSize(instrumentId);
    BigDecimal normalizedQuantity = quantity.multiply(contractMultiplier);

    TickerStats current = cache.get(instrumentId);
    if (current == null) {
      current =
          TickerStats.builder()
              .instrumentId(instrumentId)
              .lastPrice(price)
              .volume24h(normalizedQuantity)
              .turnover24h(price.multiply(normalizedQuantity))
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
    current.setVolume24h(current.getVolume24h().add(normalizedQuantity));
    current.setTurnover24h(current.getTurnover24h().add(price.multiply(normalizedQuantity)));
    current.setHigh24h(price.max(current.getHigh24h()));
    current.setLow24h(price.min(current.getLow24h()));
    if (current.getOpen24h() == null) {
      current.setOpen24h(price);
    }
    BigDecimal priceChange = price.subtract(current.getOpen24h());
    current.setPriceChange24h(priceChange);
    if (current.getOpen24h().compareTo(BigDecimal.ZERO) > 0) {
      current.setPriceChangePct(
          price.divide(current.getOpen24h(), 8, java.math.RoundingMode.HALF_UP));
    }
    cache.put(instrumentId, current);

    markPriceCacheService.record(instrumentId, tradeId, price, executedAt);
    klineAggregationService.recordTrade(instrumentId, price, quantity, executedAt, null);
  }

  private BigDecimal requireContractSize(Long instrumentId) {
    return instrumentCache
        .get(instrumentId)
        .map(instrument -> instrument.contractSize())
        .filter(contractSize -> contractSize != null && contractSize.compareTo(BigDecimal.ZERO) > 0)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Instrument cache missing or invalid contractSize for instrumentId="
                        + instrumentId));
  }

  public TickerStats get(Long instrumentId) {
    if (instrumentId == null) {
      return null;
    }
    return cache.computeIfAbsent(instrumentId, this::createDefault);
  }

  private TickerStats createDefault(Long instrumentId) {
    Instant now = Instant.now();
    BigDecimal lastPrice =
        markPriceCacheService
            .getLatest(instrumentId)
            .map(MarkPriceSnapshot::getMarkPrice)
            .filter(Objects::nonNull)
            .orElse(BigDecimal.ONE);
    return TickerStats.builder()
        .instrumentId(instrumentId)
        .lastPrice(lastPrice)
        .volume24h(BigDecimal.ZERO)
        .turnover24h(BigDecimal.ZERO)
        .high24h(lastPrice)
        .low24h(lastPrice)
        .open24h(lastPrice)
        .priceChange24h(BigDecimal.ZERO)
        .priceChangePct(BigDecimal.ZERO)
        .build();
  }
}
