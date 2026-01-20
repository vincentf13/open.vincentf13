package open.vincentf13.sdk.core.metrics;

import io.micrometer.core.instrument.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 負責計時器 (Timer) 相關的指標操作。
 * <p>
 * 使用 Timer 可以取得以下指標:
 * <ul>
 *   <li><b>{name}.count</b>: 執行的總次數 (可用於計算每秒請求數 QPS)</li>
 *   <li><b>{name}.sum</b>: 執行的總耗時</li>
 *   <li><b>{name}.max</b>: 監測期間內的最大單次耗時</li>
 *   <li><b>{name} (Percentiles)</b>: 預設包含 P50, P95, P99 分位數，用於觀察長尾延遲</li>
 * </ul>
 */
public final class MTimer {

    /**
     * 快取已建立的計時器實例。
     * 用於優化頻繁計時操作的效能，避免重複查表與物件分配。
     */
    private static final ConcurrentHashMap<String, Timer> CACHE_TIMER = new ConcurrentHashMap<>();

    private MTimer() {}

    /**
     * 記錄 Callable 執行的時間。
     */
    public static <T> T record(String name, Callable<T> c, String... tags) throws Exception {
        Timer.Sample s = Timer.start(Metrics.getRegistry());
        try {
            return c.call();
        } finally {
            s.stop(get(name, tags));
        }
    }

    /**
     * 記錄 Runnable 執行的時間。
     */
    public static void record(String name, Runnable r, String... tags) {
        Timer.Sample s = Timer.start(Metrics.getRegistry());
        try {
            r.run();
        } finally {
            s.stop(get(name, tags));
        }
    }

    private static Timer get(String name, String... tags) {
        String key = Metrics.key(name, tags);
        return CACHE_TIMER.computeIfAbsent(
            key,
            k ->
                Timer.builder(name)
                    .tags(tags)
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .publishPercentileHistogram()
                    .register(Metrics.getRegistry()));
    }
}
