package open.vincentf13.sdk.core.bootstrap;

import open.vincentf13.sdk.core.log.CoreEvent;
import open.vincentf13.sdk.core.log.OpenLog;

public abstract class OpenStartupCacheLoader {

    private static final long DEFAULT_RETRY_DELAY_MS = 3000;

    public final void loadCaches() {
        OpenLog.info(CoreEvent.STARTUP_CACHE_LOADING);
        int attempt = 0;
        long retryDelayMs = retryDelayMs();
        while (true) {
            try {
                attempt++;
                OpenLog.info(CoreEvent.STARTUP_CACHE_LOADING, "attempt", attempt, "retryDelayMs", retryDelayMs);
                doLoadCaches();
                OpenLog.info(CoreEvent.STARTUP_CACHE_LOADED);
                return;
            } catch (Exception e) {
                OpenLog.error(CoreEvent.STARTUP_CACHE_LOAD_FAILED, e, "attempt", attempt, "retryDelayMs", retryDelayMs);
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Cache loading interrupted", ie);
                }
            }
        }
    }

    protected long retryDelayMs() {
        return DEFAULT_RETRY_DELAY_MS;
    }

    protected abstract void doLoadCaches();

}
