package open.vincentf13.exchange.account.infra.bootstrap;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.infra.AccountEvent;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class ApplicationStartupListener {

    private final AccountStartupCacheLoader accountStartupCacheLoader;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        OpenLog.info(AccountEvent.STARTUP_CACHE_LOADING,
                     "Application ready, starting cache initialization");

        try {
            accountStartupCacheLoader.loadCaches();
            OpenLog.info(AccountEvent.STARTUP_CACHE_LOADED);
        } catch (Exception e) {
            OpenLog.error(AccountEvent.STARTUP_CACHE_LOAD_FAILED, e);
            throw e;
        }
    }
}
