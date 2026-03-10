package open.vincentf13.exchange.position.infra.bootstrap;

import open.vincentf13.sdk.core.bootstrap.OpenApplicationStartupListener;
import org.springframework.stereotype.Component;

/** Listens to application startup events and triggers cache initialization. */
@Component
public class ApplicationStartupListener extends OpenApplicationStartupListener {

  public ApplicationStartupListener(StartupCacheLoader startupCacheLoader) {
    super(startupCacheLoader);
  }
}
