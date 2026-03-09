package open.vincentf13.exchange.risk.infra.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.market.sdk.rest.client.ExchangeMarketClient;
import open.vincentf13.exchange.risk.infra.RiskEvent;
import open.vincentf13.exchange.risk.infra.cache.InstrumentCache;
import open.vincentf13.exchange.risk.infra.cache.MarkPriceCache;
import open.vincentf13.sdk.core.bootstrap.OpenStartupCacheLoader;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartupCacheLoader extends OpenStartupCacheLoader {

  private final ExchangeAdminClient adminClient;
  private final ExchangeMarketClient marketClient;
  private final InstrumentCache instrumentCache;
  private final MarkPriceCache markPriceCache;

  @Override
  protected void doLoadCaches() {
    loadInstruments();
    loadMarkPrices();
  }

  private void loadInstruments() {
    OpenLog.info(log, RiskEvent.STARTUP_LOADING_INSTRUMENTS);

    List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

    instrumentCache.putAll(instruments);

    OpenLog.info(log, RiskEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
  }

  private void loadMarkPrices() {
    OpenLog.info(log, RiskEvent.STARTUP_MARK_PRICE_LOAD_START);
    var response = marketClient.getAllMarkPrices();
    if (response != null && response.data() != null) {
      response.data().forEach(
          markPrice -> {
            if (markPrice.getMarkPrice() != null) {
              markPriceCache.put(markPrice.getInstrumentId(), markPrice.getMarkPrice());
            }
          });
      OpenLog.info(log, RiskEvent.STARTUP_MARK_PRICE_LOADED, "count", response.data().size());
    }
  }
}
