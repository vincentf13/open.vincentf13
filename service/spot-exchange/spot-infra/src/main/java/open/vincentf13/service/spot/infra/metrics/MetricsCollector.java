package open.vincentf13.service.spot.infra.metrics;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.HdrHistogram.Histogram;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 統一指標採集器 (Metrics Collector)
 * 職責：高性能、零裝箱的指標收集與定期持久化。
 */
@Slf4j
public class MetricsCollector {

    private static final int MAX_METRICS = 512;
    private static final LongAdder[] COUNTERS = new LongAdder[MAX_METRICS];
    private static final long[] GAUGES = new long[MAX_METRICS];
    private static final boolean[] GAUGE_DIRTY = new boolean[MAX_METRICS];
    private static final Histogram[] HISTOGRAMS = new Histogram[MAX_METRICS];

    private static long last10sWindow = 0;

    // 複用對象以減少 GC (僅由單一 FLUSHER 執行緒使用)
    private static final LongValue K = new LongValue();
    private static final LongValue V = new LongValue();

    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-flusher");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    static {
        for (int i = 0; i < MAX_METRICS; i++) {
            COUNTERS[i] = new LongAdder();
            HISTOGRAMS[i] = new Histogram(TimeUnit.SECONDS.toNanos(30), 3);
        }
        FLUSHER.scheduleAtFixedRate(MetricsCollector::flush, 100, 100, TimeUnit.MILLISECONDS);
    }

    public static void increment(long key) { add(key, 1); }

    public static void add(long key, long delta) {
        if (delta > 0) COUNTERS[idx(key)].add(delta);
    }

    public static void set(long key, long value) {
        int i = idx(key);
        GAUGES[i] = value;
        GAUGE_DIRTY[i] = true;
    }

    public static void recordLatency(long key, long nanos) {
        if (latencyValid(nanos)) {
            HISTOGRAMS[idx(key)].recordValue(Math.min(nanos, TimeUnit.SECONDS.toNanos(30)));
        }
    }

    public static void recordCpuAffinity(long key, int cpuId) { set(key, cpuId); }

    private static int idx(long key) { return (int) Math.abs(key) % MAX_METRICS; }
    private static boolean latencyValid(long ns) { return ns > 0; }

    private static void flush() {
        try {
            final long now = System.currentTimeMillis();
            final Storage s = Storage.self();

            // 1. 處理 10 秒區間延遲歷史
            if (now - last10sWindow >= 10000) {
                last10sWindow = (now / 10000) * 10000;
                flushLatencyHistory(s, last10sWindow, MetricsKey.LATENCY_MATCHING);
                flushLatencyHistory(s, last10sWindow, MetricsKey.LATENCY_TRANSPORT);
            }

            // 2. 處理累計器與 TPS 軌跡
            for (int i = 0; i < MAX_METRICS; i++) {
                long val = COUNTERS[i].sum();
                if (val > 0) {
                    write(s.latestMetrics(), -i, val);
                    if (i == (int)Math.abs(MetricsKey.MATCH_COUNT)) {
                        write(s.tpsHistory(), now, val);
                    }
                }
            }

            // 3. 處理絕對值指標
            for (int i = 0; i < MAX_METRICS; i++) {
                if (GAUGE_DIRTY[i]) {
                    write(s.latestMetrics(), -i, GAUGES[i]);
                    // GAUGE_DIRTY[i] = false; // 視需求決定是否重置，通常 Gauge 保持最新值即可
                }
            }
        } catch (Exception e) {
            log.error("Metrics flush failed: {}", e.getMessage());
        }
    }

    private static void flushLatencyHistory(Storage s, long windowId, long key) {
        Histogram h = HISTOGRAMS[idx(key)];
        if (h.getTotalCount() == 0) return;

        long p50 = h.getValueAtPercentile(50.0);
        long p90 = h.getValueAtPercentile(90.0);
        long p99 = h.getValueAtPercentile(99.0);
        long p999 = h.getValueAtPercentile(99.9);
        long max = h.getMaxValue();

        // 更新即時指標
        set(key - 100, p50); // 這裡需要一個對應關係，或是簡化 Constants
        // 為簡化，暫時直接存儲到歷史 Map，API 從歷史 Map 拿最新一筆即可
        
        save(s.latencyHistory(), windowId, key, MetricsKey.P50, p50);
        save(s.latencyHistory(), windowId, key, MetricsKey.P90, p90);
        save(s.latencyHistory(), windowId, key, MetricsKey.P99, p99);
        save(s.latencyHistory(), windowId, key, MetricsKey.P999, p999);
        save(s.latencyHistory(), windowId, key, MetricsKey.MAX, max);

        h.reset();
    }

    private static void write(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long k, long v) {
        K.set(k); V.set(v);
        map.put(K, V);
    }

    private static void save(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long window, long mKey, long pKey, long val) {
        // 編碼：(MetricKey * 10^15) + (PercentileKey * 10^12) + WindowId
        long encoded = (Math.abs(mKey) * 1_000_000_000_000_000L) + (pKey * 1_000_000_000_000L) + (window / 1000);
        write(map, encoded, val);
    }

    public static void shutdown() { flush(); FLUSHER.shutdown(); }
}
