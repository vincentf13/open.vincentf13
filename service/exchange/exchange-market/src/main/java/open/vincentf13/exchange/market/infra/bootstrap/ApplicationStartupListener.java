package open.vincentf13.exchange.market.infra.bootstrap;

import open.vincentf13.sdk.core.bootstrap.OpenApplicationStartupListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupListener extends OpenApplicationStartupListener {

    public ApplicationStartupListener(MarketStartupCacheLoader marketStartupCacheLoader) {
        super(marketStartupCacheLoader);
    }
}
