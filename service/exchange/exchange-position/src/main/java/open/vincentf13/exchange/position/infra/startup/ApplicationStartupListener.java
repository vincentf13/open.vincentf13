package open.vincentf13.exchange.position.infra.startup;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.infra.PositionLogEvent;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to application startup events and triggers cache initialization.
 */
@Component
@RequiredArgsConstructor
public class ApplicationStartupListener {

    private final StartupCacheLoader startupCacheLoader;

    /**
     * Handles the ApplicationReadyEvent to load caches after application startup.
     *
     * @param event The ApplicationReadyEvent
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        OpenLog.info(PositionLogEvent.STARTUP_CACHE_LOADING,
                "Application ready, starting cache initialization");

        try {
            startupCacheLoader.loadCaches();
            OpenLog.info(PositionLogEvent.STARTUP_CACHE_LOADED);
        } catch (Exception e) {
            OpenLog.error(PositionLogEvent.STARTUP_CACHE_LOAD_FAILED, e);
            throw e;
        }
    }
}
