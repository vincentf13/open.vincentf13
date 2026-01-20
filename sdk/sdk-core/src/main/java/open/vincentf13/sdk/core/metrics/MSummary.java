package open.vincentf13.sdk.core.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import open.vincentf13.sdk.core.metrics.enums.IMetric;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 負責數值分佈統計 (DistributionSummary) 相關的操作。
 * <p>
 * 用於統計事件的數值分佈情況，例如：
 * <ul>
 *   <li>HTTP Payload 大小 (bytes)</li>
 *   <li>佇列深度</li>
 *   <li>批次處理的筆數</li>
 * </ul>
 * 它會提供 count, sum, max, mean 等統計值，並支援百分位數 (Percentiles)。
 */
public final class MSummary {

    /**
     * 快取已建立的 DistributionSummary 實例。
     */
    private static final ConcurrentHashMap<String, DistributionSummary> CACHE = new ConcurrentHashMap<>();

    private MSummary() {}

    /**
     * 記錄一個數值。
     *
     * @param metric 指標定義 (Enum)
     * @param amount 數值 (如 bytes 大小、筆數)
     * @param tags   標籤
     */
    public static void record(IMetric metric, double amount, String... tags) {
        MetricValidator.validate(metric, tags);
        get(metric.getName(), tags).record(amount);
    }

    private static DistributionSummary get(String name, String... tags) {
        String key = Metrics.key(name, tags);
        return CACHE.computeIfAbsent(
            key,
            k ->
                DistributionSummary.builder(name)
                    .tags(tags)
                    .publishPercentiles(0.5, 0.95, 0.99) // 預設發佈 P50, P95, P99
                    .publishPercentileHistogram()        // 發佈 Histogram 供後端計算任意百分位數
                    .register(Metrics.getRegistry()));
    }
}