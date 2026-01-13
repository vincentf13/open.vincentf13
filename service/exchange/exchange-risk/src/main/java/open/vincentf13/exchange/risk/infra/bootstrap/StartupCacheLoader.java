package open.vincentf13.exchange.risk.infra.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.market.sdk.rest.client.ExchangeMarketClient;
import open.vincentf13.exchange.risk.infra.RiskEvent;
import open.vincentf13.exchange.risk.infra.cache.InstrumentCache;
import open.vincentf13.exchange.risk.infra.cache.MarkPriceCache;
import open.vincentf13.sdk.core.bootstrap.OpenStartupCacheLoader;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

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
    OpenLog.info(RiskEvent.STARTUP_LOADING_INSTRUMENTS);

    List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

    instrumentCache.putAll(instruments);

    OpenLog.info(RiskEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
  }

  private void loadMarkPrices() {
    instrumentCache
        .getAll()
        .forEach(
            instrument -> {
              try {
                var response = marketClient.getMarkPrice(instrument.instrumentId());
                if (response.isSuccess() && response.data() != null) {
                  markPriceCache.put(instrument.instrumentId(), response.data().getMarkPrice());
                }
              } catch (Exception e) {
                OpenLog.warn(
                    RiskEvent.STARTUP_MARK_PRICE_LOAD_FAILED,
                    e,
                    "instrumentId",
                    instrument.instrumentId());
              }
            });
  }
}
