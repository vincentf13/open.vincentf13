package open.vincentf13.exchange.matching.infra.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.exchange.matching.infra.cache.InstrumentCache;
import open.vincentf13.sdk.core.bootstrap.OpenStartupCacheLoader;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StartupCacheLoader extends OpenStartupCacheLoader {

    private final ExchangeAdminClient adminClient;
    private final InstrumentCache instrumentCache;

    @Override
    protected void doLoadCaches() {
        loadInstruments();
    }

    private void loadInstruments() {
        OpenLog.info(MatchingEvent.STARTUP_LOADING_INSTRUMENTS);

        List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();
        if (instruments == null || instruments.isEmpty()) {
            OpenLog.warn(MatchingEvent.STARTUP_CACHE_LOAD_PARTIAL, "message", "no instruments returned");
            return;
        }

        instrumentCache.putAll(instruments);
        OpenLog.info(MatchingEvent.STARTUP_INSTRUMENTS_LOADED, "count", instrumentCache.size());
    }
}
