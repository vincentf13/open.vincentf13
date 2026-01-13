package open.vincentf13.exchange.market.infra.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.market.infra.MarketEvent;
import open.vincentf13.exchange.market.infra.cache.InstrumentCache;
import open.vincentf13.sdk.core.bootstrap.OpenStartupCacheLoader;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketStartupCacheLoader extends OpenStartupCacheLoader {

  private final ExchangeAdminClient adminClient;
  private final InstrumentCache instrumentCache;

  @Override
  protected void doLoadCaches() {
    loadInstruments();
  }

  private void loadInstruments() {
    OpenLog.info(MarketEvent.STARTUP_LOADING_INSTRUMENTS);

    List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

    instrumentCache.putAll(instruments);

    OpenLog.info(MarketEvent.STARTUP_INSTRUMENTS_LOADED, "count", instruments.size());
  }
}
