package open.vincentf13.exchange.test.client;

public abstract class BaseClient {
    protected final String host;

    protected BaseClient(String host) {
        this.host = host;
    }
}
