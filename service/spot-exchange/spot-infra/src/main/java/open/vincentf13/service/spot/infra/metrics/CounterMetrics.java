package open.vincentf13.service.spot.infra.metrics;

/**
 * 計數器指標 (Counter Metrics)
 * 職責：靜態入口，代理至 StaticMetricsHolder。
 */
public class CounterMetrics {
    public static void increment(long key) {
        StaticMetricsHolder.addCounter(key, 1);
    }

    public static void add(long key, long delta) {
        StaticMetricsHolder.addCounter(key, delta);
    }
}
