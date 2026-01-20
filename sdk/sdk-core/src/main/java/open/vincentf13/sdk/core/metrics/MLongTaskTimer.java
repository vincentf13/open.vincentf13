package open.vincentf13.sdk.core.metrics;

import io.micrometer.core.instrument.LongTaskTimer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 負責長任務計時器 (LongTaskTimer) 相關的操作。
 * <p>
 * LongTaskTimer 與普通 Timer 不同，它專門用於追蹤<b>正在進行中</b>的任務。
 * 它會提供：
 * <ul>
 *   <li><b>active tasks</b>: 當前正在執行的任務數量</li>
 *   <li><b>duration</b>: 當前所有執行中任務的累積時間</li>
 *   <li><b>max</b>: 當前執行最久的任務時間</li>
 * </ul>
 */
public final class MLongTaskTimer {

    /**
     * 快取已建立的長任務計時器實例。
     */
    private static final ConcurrentHashMap<String, LongTaskTimer> CACHE = new ConcurrentHashMap<>();

    private MLongTaskTimer() {}

    /**
     * 開始一個長任務計時。
     * <p>
     * 務必在 finally 區塊中呼叫 {@link #stop(LongTaskTimer.Sample)}。
     *
     * @param metric 指標定義 (Enum)
     * @param tags   標籤
     * @return 任務樣本 (Sample)，用於停止計時
     */
    public static LongTaskTimer.Sample start(IMetric metric, String... tags) {
        MetricValidator.validate(metric, tags);
        return get(metric.getName(), tags).start();
    }

    /**
     * 停止長任務計時。
     *
     * @param sample {@link #start(IMetric, String...)} 返回的樣本物件
     */
    public static void stop(LongTaskTimer.Sample sample) {
        if (sample != null) {
            sample.stop();
        }
    }

    private static LongTaskTimer get(String name, String... tags) {
        String key = Metrics.key(name, tags);
        return CACHE.computeIfAbsent(key, k -> LongTaskTimer.builder(name).tags(tags).register(Metrics.getRegistry()));
    }
}