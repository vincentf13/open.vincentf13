package open.vincentf13.service.spot.infra.metrics;

/**
 * 絕對值指標 (Gauge Metrics)
 * 職責：靜態入口，代理至 StaticMetricsHolder。
 */
public class GaugeMetrics {
    public static void set(long key, long value) {
        StaticMetricsHolder.setGauge(key, value);
    }
}
