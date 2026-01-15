package open.vincentf13.exchange.position.infra.bootstrap;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.market.sdk.rest.client.ExchangeMarketClient;
import open.vincentf13.exchange.position.infra.PositionEvent;
import open.vincentf13.exchange.position.infra.cache.InstrumentCache;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.position.infra.cache.RiskLimitCache;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;
import open.vincentf13.exchange.risk.sdk.rest.client.ExchangeRiskClient;
import open.vincentf13.sdk.core.bootstrap.OpenStartupCacheLoader;
import open.vincentf13.sdk.core.log.CoreEvent;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

/** Service responsible for loading instrument and risk limit data at application startup. */
@Service
@RequiredArgsConstructor
public class StartupCacheLoader extends OpenStartupCacheLoader {

  private final ExchangeAdminClient adminClient;
  private final ExchangeRiskClient riskClient;
  private final ExchangeMarketClient marketClient;
  private final InstrumentCache instrumentCache;
  private final MarkPriceCache markPriceCache;
  private final RiskLimitCache riskLimitCache;

  /**
   * Loads all instruments and their risk limits into local caches.
   *
   * @throws RuntimeException if loading fails after max retry attempts
   */
  @Override
  protected void doLoadCaches() {
    loadInstruments();
    loadRiskLimits();
    loadMarkPrices();
  }

  /** Loads all instruments from Admin service and stores them in the cache. */
  private void loadInstruments() {
    OpenLog.info(PositionEvent.STARTUP_LOADING_INSTRUMENTS);

    List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

    instrumentCache.putAll(instruments);

    OpenLog.info(PositionEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
  }

  /** Loads risk limits for all instruments from Risk service and stores them in the cache. */
  private void loadRiskLimits() {
    OpenLog.info(
        PositionEvent.STARTUP_LOADING_RISK_LIMITS, "instrumentCount", instrumentCache.size());

    List<RiskLimitResponse> riskLimits = riskClient.list(null).data();

    if (riskLimits != null) {
      riskLimits.forEach(
          riskLimit -> riskLimitCache.put(riskLimit.instrumentId(), riskLimit));
      OpenLog.info(PositionEvent.STARTUP_RISK_LIMITS_LOADED, "count", riskLimits.size());
    }
  }

  private void loadMarkPrices() {
    OpenLog.info(CoreEvent.STARTUP_CACHE_LOADING, "Loading mark prices");
    var response = marketClient.getAllMarkPrices();
    if (response != null && response.data() != null) {
      response.data().forEach(
          markPrice -> {
            if (markPrice.getMarkPrice() != null) {
              markPriceCache.update(
                  markPrice.getInstrumentId(),
                  markPrice.getMarkPrice(),
                  markPrice.getCalculatedAt());
            }
          });
      OpenLog.info(PositionEvent.STARTUP_MARK_PRICES_LOADED, "count", response.data().size());
    }
  }
}
