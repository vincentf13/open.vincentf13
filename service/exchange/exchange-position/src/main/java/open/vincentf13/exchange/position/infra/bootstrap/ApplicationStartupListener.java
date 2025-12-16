package open.vincentf13.exchange.position.infra.bootstrap;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.sdk.rest.api.MarketApi;
import open.vincentf13.exchange.market.sdk.rest.api.dto.MarkPriceResponse;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.PositionEvent;
import open.vincentf13.exchange.position.infra.cache.InstrumentCache;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listens to application startup events and triggers cache initialization.
 */
@Component
@RequiredArgsConstructor
public class ApplicationStartupListener {

    private final StartupCacheLoader startupCacheLoader;
    private final MarketApi marketApi;
    private final MarkPriceCache markPriceCache;
    private final InstrumentCache instrumentCache;
    private final PositionDomainService positionDomainService;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Handles the ApplicationReadyEvent to load caches after application startup.
     *
     * @param event The ApplicationReadyEvent
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        OpenLog.info(PositionEvent.STARTUP_CACHE_LOADING,
                     "Application ready, starting cache initialization");

        try {
            startupCacheLoader.loadCaches();
            preloadMarkPrices();
            OpenLog.info(PositionEvent.STARTUP_CACHE_LOADED);
        } catch (Exception e) {
            OpenLog.error(PositionEvent.STARTUP_CACHE_LOAD_FAILED, e);
            throw e;
        }
    }
    
    private void preloadMarkPrices() {
        instrumentCache.getAll().forEach(instrument -> {
            Long instrumentId = instrument.instrumentId();
            try {
                OpenApiResponse<MarkPriceResponse> response = marketApi.getMarkPrice(instrumentId);
                if (response.isSuccess() && response.data() != null) {
                    MarkPriceResponse data = response.data();
                    markPriceCache.update(data.getInstrumentId(), data.getMarkPrice(), data.getCalculatedAt());
                    positionDomainService.updateMarkPrice(data.getInstrumentId(), data.getMarkPrice());
                } else {
                    OpenLog.warn(PositionEvent.STARTUP_CACHE_LOAD_PARTIAL,
                                 "instrumentId", instrumentId,
                                 "reason", response.message());
                }
            } catch (Exception e) {
                OpenLog.warn(PositionEvent.STARTUP_CACHE_LOAD_PARTIAL, e, "instrumentId", instrumentId);
            }
        });
    }
}
