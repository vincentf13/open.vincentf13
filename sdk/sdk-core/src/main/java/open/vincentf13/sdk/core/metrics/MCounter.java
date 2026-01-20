package open.vincentf13.sdk.core.metrics;

import io.micrometer.core.instrument.Counter;
import open.vincentf13.sdk.core.metrics.enums.IMetric;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 負責計數器 (Counter) 相關的指標操作。
 * <p>
 * 使用 Counter 可以取得以下指標:
 * <ul>
 *   <li><b>{name}.count</b>: 累積的總次數 (速率)</li>
 * </ul>
 */
public final class MCounter {

    /**
     * 快取已建立的計數器實例。
     * 理由：
     * 1. 避免每次呼叫時重複執行 Counter.builder() 產生暫存物件，降低 GC 壓力。
     * 2. ConcurrentHashMap 查找速度極快，比 Micrometer 內部複雜的 MeterId 查找更具效能。
     * 3. 確保在熱點代碼 (Hot Path) 中呼叫時，效能損耗降至最低。
     */
    private static final ConcurrentHashMap<String, Counter> CACHE = new ConcurrentHashMap<>();

    private MCounter() {}

    /**
     * 增加計數器數值。
     *
     * @param metric 指標定義 (Enum)
     * @param step   增加的數值
     * @param tags   標籤 (key, value 配對)
     */
    public static void inc(IMetric metric, double step, String... tags) {
        MetricValidator.validate(metric, tags);
        get(metric.getName(), tags).increment(step);
    }

    /**
     * 計數器數值加一。
     *
     * @param metric 指標定義 (Enum)
     * @param tags   標籤 (key, value 配對)
     */
    public static void one(IMetric metric, String... tags) {
        MetricValidator.validate(metric, tags);
        get(metric.getName(), tags).increment();
    }

    private static Counter get(String name, String... tags) {
        String key = Metrics.key(name, tags);
        return CACHE.computeIfAbsent(key, k -> Counter.builder(name).tags(tags).register(Metrics.getRegistry()));
    }
}