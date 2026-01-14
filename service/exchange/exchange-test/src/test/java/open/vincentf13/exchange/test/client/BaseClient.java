package open.vincentf13.exchange.test.client;

public abstract class BaseClient {
    private static String host;

    public static void setHost(String host) {
        BaseClient.host = host;
    }

    protected static String host() {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("Host is required");
        }
        return host;
    }
}
