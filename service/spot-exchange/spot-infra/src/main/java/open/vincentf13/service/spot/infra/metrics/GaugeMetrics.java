package open.vincentf13.service.spot.infra.metrics;

import java.util.function.BiConsumer;

/**
 * 絕對值指標 (Gauge Metrics)
 * 職責：按類型分類的指標數據持有者，提供靜態記錄 API。
 */
public class GaugeMetrics {
    private static final int MAX = 512;
    static final GaugeMetrics INSTANCE = new GaugeMetrics(MAX);

    private final long[] gauges;
    private final boolean[] dirty;

    private GaugeMetrics(int size) {
        this.gauges = new long[size];
        this.dirty = new boolean[size];
    }

    /** 設置指標值 (靜態門戶) */
    public static void set(long key, long value) {
        int i = idx(key);
        INSTANCE.gauges[i] = value;
        INSTANCE.dirty[i] = true;
    }

    /** 遍歷所有髒指標並清除髒標記 (供 Writer 使用) */
    void forEachDirty(BiConsumer<Integer, Long> action) {
        for (int i = 0; i < INSTANCE.gauges.length; i++) {
            if (INSTANCE.dirty[i]) {
                action.accept(i, INSTANCE.gauges[i]);
                INSTANCE.dirty[i] = false;
            }
        }
    }

    private static int idx(long key) {
        return (int) Math.abs(key) % MAX;
    }
}
