package open.vincentf13.sdk.core.bootstrap;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

public abstract class OpenApplicationStartupListener {

  private final OpenStartupCacheLoader cacheLoader;
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  protected OpenApplicationStartupListener(OpenStartupCacheLoader cacheLoader) {
    this.cacheLoader = cacheLoader;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady(ApplicationReadyEvent event) {
    if (event.getApplicationContext().getParent() != null) {
      return;
    }
    if (!initialized.compareAndSet(false, true)) {
      return;
    }
    if (cacheLoader != null) {
      cacheLoader.loadCaches();
    }
    afterCacheLoaded();
  }

  protected void afterCacheLoaded() {}
}
