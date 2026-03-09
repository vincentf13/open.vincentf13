package open.vincentf13.exchange.risk.infra.bootstrap;

import open.vincentf13.sdk.core.bootstrap.OpenApplicationStartupListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupListener extends OpenApplicationStartupListener {

  public ApplicationStartupListener(StartupCacheLoader startupCacheLoader) {
    super(startupCacheLoader);
  }
}
