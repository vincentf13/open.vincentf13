package open.vincentf13.exchange.matching.infra.bootstrap;

import open.vincentf13.exchange.matching.infra.loader.WalLoader;
import open.vincentf13.exchange.matching.service.MatchingEngine;
import open.vincentf13.sdk.core.bootstrap.OpenApplicationStartupListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupListener extends OpenApplicationStartupListener {

  private final MatchingEngine matchingEngine;
  private final WalLoader walLoader;

  public ApplicationStartupListener(
      MatchingEngine matchingEngine, WalLoader walLoader, StartupCacheLoader startupCacheLoader) {
    super(startupCacheLoader);
    this.matchingEngine = matchingEngine;
    this.walLoader = walLoader;
  }

  @Override
  protected void afterCacheLoaded() {
    walLoader.init();
    matchingEngine.init();
  }
}
