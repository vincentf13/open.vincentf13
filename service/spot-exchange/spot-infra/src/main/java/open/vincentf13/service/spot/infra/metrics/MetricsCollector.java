package open.vincentf13.service.spot.infra.metrics;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.HdrHistogram.Histogram;

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

    // 直方圖陣列 (用於延遲分佈)
    private static final Histogram[] HISTOGRAM_ARRAY = new Histogram[MAX_METRICS];
    private static long last10sFlushTime = System.currentTimeMillis();

    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-flusher");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    static {
        for (int i = 0; i < MAX_METRICS; i++) {
            COUNTER_ARRAY[i] = new LongAdder();
            // 初始化直方圖 (最高 30 秒即 30,000,000,000 奈秒，精度 3 位)
            HISTOGRAM_ARRAY[i] = new Histogram(30_000_000_000L, 3);
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

    /** 記錄延遲直方圖 */
    public static void recordLatency(long key, long latencyNs) {
        if (latencyNs <= 0) return;
        HISTOGRAM_ARRAY[getIndex(key)].recordValue(Math.min(latencyNs, 30_000_000_000L));
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
            final long now = System.currentTimeMillis();
            
            // 每 10 秒處理一次直方圖百分位數 (轉換為 Gauge 並存儲歷史區間)
            if (now - last10sFlushTime >= 10000) {
                final long windowId = (now / 10000) * 10000; // 修正：使用正確的毫秒時間戳 (10秒對齊)
                updatePercentilesAndHistory(windowId, MetricsKey.MATCHING_PROCESS_LATENCY_NS, 
                                 MetricsKey.MATCHING_LATENCY_P50, MetricsKey.MATCHING_LATENCY_P90, 
                                 MetricsKey.MATCHING_LATENCY_P99, MetricsKey.MATCHING_LATENCY_P999, MetricsKey.MATCHING_LATENCY_MAX);
                
                updatePercentilesAndHistory(windowId, MetricsKey.TRANSPORT_LATENCY_NS, 
                                 MetricsKey.TRANSPORT_LATENCY_P50, MetricsKey.TRANSPORT_LATENCY_P90, 
                                 MetricsKey.TRANSPORT_LATENCY_P99, MetricsKey.TRANSPORT_LATENCY_P999, MetricsKey.TRANSPORT_LATENCY_MAX);
                
                last10sFlushTime = now;
            }

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

    private static void updatePercentilesAndHistory(long windowId, long sourceKey, long p50, long p90, long p99, long p999, long max) {
        Histogram h = HISTOGRAM_ARRAY[getIndex(sourceKey)];
        if (h.getTotalCount() > 0) {
            long v50 = h.getValueAtPercentile(50.0);
            long v90 = h.getValueAtPercentile(90.0);
            long v99 = h.getValueAtPercentile(99.0);
            long v999 = h.getValueAtPercentile(99.9);
            long vMax = h.getMaxValue();

            // 更新目前的 Gauge (供 Saturation 接口獲取最新值)
            set(p50, v50); set(p90, v90); set(p99, v99); set(p999, v999); set(max, vMax);
            
            // 存儲歷史區間數據 (Encoded Key)
            saveHistory(windowId, p50, v50);
            saveHistory(windowId, p90, v90);
            saveHistory(windowId, p99, v99);
            saveHistory(windowId, p999, v999);
            saveHistory(windowId, max, vMax);

            h.reset();
        }
    }

    private static void saveHistory(long windowId, long metricKey, long value) {
        // Key 編碼：(絕對值 MetricKey * 10^15) + WindowId
        // 使用 10^15 確保 Key 空間與毫秒級時間戳 (10^12) 徹底分離
        long encodedKey = (Math.abs(metricKey) * 1_000_000_000_000_000L) + windowId;
        REUSABLE_KEY_1.set(encodedKey);
        REUSABLE_VAL_1.set(value);
        Storage.self().metricsHistory().put(REUSABLE_KEY_1, REUSABLE_VAL_1);
    }
    
    public static void shutdown() {
        flush();
        FLUSHER.shutdown();
    }
}
