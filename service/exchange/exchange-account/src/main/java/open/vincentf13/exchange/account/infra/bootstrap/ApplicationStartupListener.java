package open.vincentf13.exchange.account.infra.bootstrap;

import open.vincentf13.sdk.core.bootstrap.OpenApplicationStartupListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupListener extends OpenApplicationStartupListener {

    public ApplicationStartupListener(AccountStartupCacheLoader accountStartupCacheLoader) {
        super(accountStartupCacheLoader);
    }
}
