package open.vincentf13.exchange.matching.infra.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.client.ExchangeAdminClient;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.exchange.matching.infra.cache.InstrumentCache;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StartupCacheLoader {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final ExchangeAdminClient adminClient;
    private final InstrumentCache instrumentCache;

    public void loadCaches() {
        OpenLog.info(MatchingEvent.STARTUP_CACHE_LOADING);

        int attempt = 0;
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                attempt++;
                OpenLog.info(MatchingEvent.STARTUP_CACHE_LOADING, "attempt", attempt, "maxAttempts", MAX_RETRY_ATTEMPTS);

                loadInstruments();

                OpenLog.info(MatchingEvent.STARTUP_CACHE_LOADED, "instruments", instrumentCache.size());
                return;

            } catch (Exception e) {
                OpenLog.error(MatchingEvent.STARTUP_CACHE_LOAD_FAILED, e, "attempt", attempt, "maxAttempts", MAX_RETRY_ATTEMPTS);

                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    throw new RuntimeException("Failed to load caches after " + MAX_RETRY_ATTEMPTS + " attempts", e);
                }

                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Cache loading interrupted", ie);
                }
            }
        }
    }

    private void loadInstruments() {
        OpenLog.info(MatchingEvent.STARTUP_LOADING_INSTRUMENTS);

        List<InstrumentSummaryResponse> instruments = adminClient.list(null, null).data();

        // We need detailed info (makerFee) which is not in summary
        // Since we cannot batch get details, and n+1 is bad at startup but acceptable if n is small
        // However, better solution might be to update summary DTO in SDK, but we can't do that easily here.
        // Let's assume we can fetch details for each instrument.

        List<open.vincentf13.exchange.matching.domain.instrument.Instrument> domainInstruments = instruments.stream()
            .map(summary -> {
                try {
                    var detail = adminClient.get(summary.instrumentId()).data();
                    return open.vincentf13.exchange.matching.domain.instrument.Instrument.builder()
                        .instrumentId(detail.instrumentId())
                        .symbol(detail.symbol())
                        .baseAsset(detail.baseAsset() != null ? detail.baseAsset().name() : null)
                        .quoteAsset(detail.quoteAsset() != null ? detail.quoteAsset().name() : null)
                        .makerFee(detail.makerFeeRate())
                        .takerFee(detail.takerFeeRate())
                        .build();
                } catch (Exception e) {
                   OpenLog.warn(MatchingEvent.STARTUP_CACHE_LOAD_PARTIAL, "Failed to load detail for instrument " + summary.instrumentId());
                   return null;
                }
            })
            .filter(java.util.Objects::nonNull)
            .toList();

        instrumentCache.putAllDomain(domainInstruments);

        OpenLog.info(MatchingEvent.STARTUP_INSTRUMENTS_LOADED, "count", domainInstruments.size());
    }
}
