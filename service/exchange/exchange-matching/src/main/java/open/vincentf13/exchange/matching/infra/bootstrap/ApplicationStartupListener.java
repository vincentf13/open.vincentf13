package open.vincentf13.exchange.matching.infra.bootstrap;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.infra.MatchingEvent;
import open.vincentf13.exchange.matching.infra.cache.InstrumentCache;
import open.vincentf13.exchange.matching.infra.loader.WalLoader;
import open.vincentf13.exchange.matching.service.MatchingEngine;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class ApplicationStartupListener {

    private final MatchingEngine matchingEngine;
    private final WalLoader walLoader;
    private final InstrumentCache instrumentCache;
    private final StartupCacheLoader startupCacheLoader;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        
        OpenLog.info(MatchingEvent.STARTUP_CACHE_LOADING, "Application ready, starting matching engine initialization");

        try {
            // 1. Load basic caches (instruments)
            startupCacheLoader.loadCaches();
            
            // 2. Init components
            // InstrumentCache instance is set in constructor, so no init needed.
            walLoader.init();
            matchingEngine.init();
            
            OpenLog.info(MatchingEvent.STARTUP_CACHE_LOADED);
        } catch (Exception e) {
            OpenLog.error(MatchingEvent.STARTUP_CACHE_LOAD_FAILED, e);
            throw e;
        }
    }
}
