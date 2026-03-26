package open.vincentf13.service.spot.infra.metrics;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;

/**
 * 計數器指標 (Counter Metrics)
 * 職責：按類型分類的指標數據持有者，提供靜態記錄 API。
 */
public class CounterMetrics {
    private static final int MAX = 512;
    static final CounterMetrics INSTANCE = new CounterMetrics(MAX);

    private final LongAdder[] counters;

    private CounterMetrics(int size) {
        this.counters = new LongAdder[size];
        for (int i = 0; i < size; i++) {
            counters[i] = new LongAdder();
        }
    }

    /** 累加指標 (靜態門戶) */
    public static void increment(long key) {
        add(key, 1);
    }

    /** 累加指標 (靜態門戶) */
    public static void add(long key, long delta) {
        if (delta > 0) {
            INSTANCE.counters[idx(key)].add(delta);
        }
    }

    /** 遍歷所有非零指標 (供 Writer 使用) */
    void forEach(BiConsumer<Integer, Long> action) {
        for (int i = 0; i < INSTANCE.counters.length; i++) {
            long val = INSTANCE.counters[i].sum();
            if (val > 0) {
                action.accept(i, val);
            }
        }
    }

    private static int idx(long key) {
        return (int) Math.abs(key) % MAX;
    }
}
