package open.vincentf13.service.spot.infra.metrics;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 統一監控指標管理器 (Metrics Manager) - 零對象裝箱版
 */
@Slf4j
public class MetricsCollector {

    // 核心優化：使用陣列存放熱點計數器，Key 映射到索引 (絕對值)
    // 考慮到 GC 指標在 -300 左右，我們將容量擴大至 512
    private static final int MAX_METRICS = 512;
    private static final LongAdder[] COUNTER_ARRAY = new LongAdder[MAX_METRICS];
    
    // 絕對值緩衝 (非熱點，保留 Map)
    private static final ConcurrentHashMap<Long, Long> GAUGES = new ConcurrentHashMap<>(128);

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
        // 使用絕對值索引，並確保不超過陣列邊界
        int idx = (int) Math.abs(key);
        // 如果 Key 是負數且在合理範圍內，直接作為索引
        return (idx > 0 && idx < MAX_METRICS) ? idx : 0;
    }

    public static void increment(long key) {
        COUNTER_ARRAY[getIndex(key)].increment();
    }

    public static void add(long key, long delta) {
        if (delta <= 0) return;
        COUNTER_ARRAY[getIndex(key)].add(delta);
    }

    public static void set(long key, long value) {
        GAUGES.put(key, value);
    }

    public static void recordCpuAffinity(long key, int cpuId) {
        set(key, (long) cpuId);
    }

    private static void flush() {
        try {
            var metricsMap = Storage.self().metricsHistory();
            
            // 1. 處理累計器 (從陣列讀取，無裝箱)
            // 核心優化：永遠只讀取 sum() 並覆蓋持久化層，不進行 Reset
            // 這能保證指標數據在任何取樣點都是單調遞增的，防止計算 TPS 時出現負數
            for (int i = 0; i < MAX_METRICS; i++) {
                long total = COUNTER_ARRAY[i].sum();
                if (total > 0) {
                    final long metricsKey = -((long)i);
                    // 直接寫入當前總量，覆蓋舊值
                    metricsMap.put(metricsKey, total);
                }
            }

            // 2. 處理絕對值
            GAUGES.forEach(metricsMap::put);
            
        } catch (Exception e) {
            log.error("[METRICS-FLUSH-ERROR] {}", e.getMessage());
        }
    }
    
    public static void shutdown() {
        flush();
        FLUSHER.shutdown();
    }
}
