package open.vincentf13.sdk.core.metrics;

import io.micrometer.core.instrument.Counter;
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
     * @param name 指標名稱
     * @param step 增加的數值
     * @param tags 標籤 (key, value 配對)
     */
    public static void inc(String name, double step, String... tags) {
        get(name, tags).increment(step);
    }

    /**
     * 計數器數值加一。
     *
     * @param name 指標名稱
     * @param tags 標籤 (key, value 配對)
     */
    public static void one(String name, String... tags) {
        get(name, tags).increment();
    }

    private static Counter get(String name, String... tags) {
        String key = Metrics.key(name, tags);
        return CACHE.computeIfAbsent(key, k -> Counter.builder(name).tags(tags).register(Metrics.getRegistry()));
    }
}
