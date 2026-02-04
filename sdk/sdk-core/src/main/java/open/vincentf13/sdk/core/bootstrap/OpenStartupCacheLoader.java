package open.vincentf13.sdk.core.bootstrap;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.sdk.core.log.CoreEvent;
import open.vincentf13.sdk.core.log.OpenLog;

@Slf4j
public abstract class OpenStartupCacheLoader {

  private static final long DEFAULT_RETRY_DELAY_MS = 5000;

  public final void loadCaches() {
    OpenLog.info(log, CoreEvent.STARTUP_CACHE_LOADING);
    int attempt = 0;
    long retryDelayMs = retryDelayMs();
    while (true) {
      try {
        attempt++;
        OpenLog.info(
            log,
            CoreEvent.STARTUP_CACHE_LOADING,
            "attempt",
            attempt,
            "retryDelayMs",
            retryDelayMs);
        doLoadCaches();
        OpenLog.info(log, CoreEvent.STARTUP_CACHE_LOADED);
        return;
      } catch (Exception e) {
        OpenLog.error(
            log,
            CoreEvent.STARTUP_CACHE_LOAD_FAILED,
            e,
            "attempt",
            attempt,
            "retryDelayMs",
            retryDelayMs);
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
