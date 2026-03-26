package open.vincentf13.service.spot.infra.metrics;

/**
 * 延遲指標 (Latency Metrics)
 * 職責：靜態入口，代理至 StaticMetricsHolder。
 */
public class LatencyMetrics {
    public static void record(long key, long nanos) {
        StaticMetricsHolder.recordLatency(key, nanos);
    }
}
