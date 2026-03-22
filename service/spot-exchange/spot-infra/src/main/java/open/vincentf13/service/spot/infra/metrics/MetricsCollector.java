package open.vincentf13.service.spot.infra.metrics;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 統一監控指標管理器 (Metrics Manager) - 高性能批處理版
 * 職責：
 * 1. 提供極低延遲的埋點 API (increment/add/set)
 * 2. 內部自動緩衝，避免頻繁觸發非同步任務
 * 3. 統一背景線程每 100ms 定時刷盤，徹底消除 I/O 競爭與 GC 壓力
 */
@Slf4j
public class MetricsCollector {

    // 內存計數器緩衝 (使用 ConcurrentHashMap 儲存 LongAdder)
    private static final ConcurrentHashMap<Long, LongAdder> COUNTERS = new ConcurrentHashMap<>(128);
    
    // 絕對值緩衝 (如 JVM 記憶體、CPU 負載)
    private static final ConcurrentHashMap<Long, Long> GAUGES = new ConcurrentHashMap<>(128);

    // 唯一的背景定時刷盤執行器
    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-flusher");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    static {
        // 每 100ms 執行一次批量刷盤
        FLUSHER.scheduleAtFixedRate(MetricsCollector::flush, 100, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * 極速計數：遞增 1
     * 性能優化：避免在熱點路徑調用 computeIfAbsent (其內部有裝箱開銷)
     */
    public static void increment(long key) {
        LongAdder adder = COUNTERS.get(key);
        if (adder == null) {
            // 只有初始化時才會有一次裝箱開銷
            adder = COUNTERS.computeIfAbsent(key, k -> new LongAdder());
        }
        adder.increment();
    }

    /**
     * 極速計數：增加 delta
     */
    public static void add(long key, long delta) {
        if (delta <= 0) return;
        LongAdder adder = COUNTERS.get(key);
        if (adder == null) {
            adder = COUNTERS.computeIfAbsent(key, k -> new LongAdder());
        }
        adder.add(delta);
    }

    /**
     * 設置絕對值 (Gauge)
     */
    public static void set(long key, long value) {
        GAUGES.put(key, value);
    }

    /**
     * 紀錄 CPU 綁核狀態
     */
    public static void recordCpuAffinity(long key, int cpuId) {
        set(key, (long) cpuId);
    }

    /**
     * 核心動作：將內存數據批量同步至 Chronicle Map (磁碟)
     */
    private static void flush() {
        try {
            var metricsMap = Storage.self().metricsHistory();
            
            // 1. 處理累計器 (Counters)
            COUNTERS.forEach((key, adder) -> {
                long sum = adder.sumThenReset();
                if (sum > 0) {
                    // 同步到持久化磁碟 Map
                    metricsMap.compute(key, (k, v) -> v == null ? sum : v + sum);
                }
            });

            // 2. 處理絕對值 (Gauges)
            GAUGES.forEach(metricsMap::put);
            
        } catch (Exception e) {
            log.error("[METRICS-FLUSH-ERROR] {}", e.getMessage());
        }
    }
    
    /**
     * 停機前強制刷盤
     */
    public static void shutdown() {
        log.info("MetricsManager 正在執行最後一次刷盤...");
        flush();
        FLUSHER.shutdown();
    }
}
