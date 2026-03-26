package open.vincentf13.service.spot.infra.metrics;

import org.HdrHistogram.Histogram;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 延遲指標 (Latency Metrics)
 * 職責：按類型分類的指標數據持有者，提供靜態記錄 API。
 */
public class LatencyMetrics {
    private static final int MAX = 512;
    static final LatencyMetrics INSTANCE = new LatencyMetrics(MAX);

    private final Histogram[] histograms;
    private final long maxLatencyNanos = TimeUnit.SECONDS.toNanos(30);

    private LatencyMetrics(int size) {
        this.histograms = new Histogram[size];
        for (int i = 0; i < size; i++) {
            histograms[i] = new Histogram(maxLatencyNanos, 3);
        }
    }

    /** 記錄延遲 (靜態門戶) */
    public static void record(long key, long nanos) {
        if (nanos > 0) {
            INSTANCE.histograms[idx(key)].recordValue(Math.min(nanos, INSTANCE.maxLatencyNanos));
        }
    }

    /** 提供特定的 Histogram 並重置 (供 Writer 使用) */
    void consume(long key, BiConsumer<Long, Histogram> consumer) {
        Histogram h = INSTANCE.histograms[idx(key)];
        if (h.getTotalCount() > 0) {
            consumer.accept(key, h);
            h.reset();
        }
    }

    private static int idx(long key) {
        return (int) Math.abs(key) % MAX;
    }
}
