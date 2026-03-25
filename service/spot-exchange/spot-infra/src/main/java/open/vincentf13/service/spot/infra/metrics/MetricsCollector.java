package open.vincentf13.service.spot.infra.metrics;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 統一監控指標管理器 (Metrics Manager) - 零對象裝箱 & 零鎖版
 */
@Slf4j
public class MetricsCollector {

    // 核心優化：使用陣列存放熱點計數器，Key 映射到索引 (絕對值)
    private static final int MAX_METRICS = 512;
    private static final LongAdder[] COUNTER_ARRAY = new LongAdder[MAX_METRICS];
    
    // 絕對值緩衝 (非熱點，改用 long 陣列以消除裝箱)
    private static final long[] GAUGE_ARRAY = new long[MAX_METRICS];
    private static final boolean[] GAUGE_UPDATED = new boolean[MAX_METRICS];

    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-flusher");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    static {
        for (int i = 0; i < MAX_METRICS; i++) {
            COUNTER_ARRAY[i] = new LongAdder();
        }
        FLUSHER.scheduleAtFixedRate(MetricsCollector::flush, 100, 100, TimeUnit.MILLISECONDS);
    }

    private static int getIndex(long key) {
        // MetricsKey 均為負數 (-1 ~ -512)，將其轉為正數索引 (1 ~ 512)
        int idx = (int) -key;
        return (idx >= 0 && idx < MAX_METRICS) ? idx : 0;
    }

    public static void increment(long key) {
        COUNTER_ARRAY[getIndex(key)].increment();
    }

    public static void add(long key, long delta) {
        if (delta <= 0) return;
        COUNTER_ARRAY[getIndex(key)].add(delta);
    }

    public static synchronized void set(long key, long value) {
        int idx = getIndex(key);
        GAUGE_ARRAY[idx] = value;
        GAUGE_UPDATED[idx] = true;
    }

    public static void recordCpuAffinity(long key, int cpuId) {
        set(key, (long) cpuId);
    }

    private static final open.vincentf13.service.spot.infra.chronicle.LongValue REUSABLE_KEY_1 = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private static final open.vincentf13.service.spot.infra.chronicle.LongValue REUSABLE_KEY_2 = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private static final open.vincentf13.service.spot.infra.chronicle.LongValue REUSABLE_VAL_1 = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private static final open.vincentf13.service.spot.infra.chronicle.LongValue REUSABLE_VAL_2 = new open.vincentf13.service.spot.infra.chronicle.LongValue();

    private static void flush() {
        try {
            var metricsMap = Storage.self().metricsHistory();
            
            // 1. 處理累計器 (還原為負數 Key 以對齊 Constants)
            for (int i = 0; i < MAX_METRICS; i++) {
                long total = COUNTER_ARRAY[i].sum();
                if (total > 0) {
                    REUSABLE_KEY_1.set(-((long)i));
                    REUSABLE_VAL_1.set(total);
                    metricsMap.put(REUSABLE_KEY_1, REUSABLE_VAL_1);
                }
            }

            // 2. 處理絕對值
            synchronized (MetricsCollector.class) {
                for (int i = 0; i < MAX_METRICS; i++) {
                    if (GAUGE_UPDATED[i]) {
                        REUSABLE_KEY_2.set(-((long)i));
                        REUSABLE_VAL_2.set(GAUGE_ARRAY[i]);
                        metricsMap.put(REUSABLE_KEY_2, REUSABLE_VAL_2);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("[METRICS-FLUSH-ERROR] {}", e.getMessage());
        }
    }
    
    public static void shutdown() {
        flush();
        FLUSHER.shutdown();
    }
}
