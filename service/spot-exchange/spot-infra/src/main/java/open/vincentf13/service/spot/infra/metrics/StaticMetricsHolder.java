package open.vincentf13.service.spot.infra.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指標靜態門面
 *
 * 提供 lock-free 的 counter/gauge 記錄。
 * 延遲分佈改由 BenchmarkTool 在 client 端以 HdrHistogram 量測，
 * 不再在 matching hot path 調用 nanoTime。
 */
public class StaticMetricsHolder {

    private static final ConcurrentHashMap<Long, AtomicLong> VALUES = new ConcurrentHashMap<>();

    // GC 事件緩衝：GC notification thread 寫入，MetricsWriter 批次刷盤
    public record GcEvent(long key, String meta) {}
    private static final ConcurrentLinkedQueue<GcEvent> PENDING_GC_EVENTS = new ConcurrentLinkedQueue<>();

    public static void addCounter(long key, long delta) {
        VALUES.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(delta);
    }

    public static void setGauge(long key, long val) {
        VALUES.computeIfAbsent(key, k -> new AtomicLong()).set(val);
    }

    public static void recordCpuId(long historyKey, long currentKey, int cpuId) {
        if (cpuId < 0 || cpuId >= 64) return;
        VALUES.computeIfAbsent(historyKey, k -> new AtomicLong()).accumulateAndGet(1L << cpuId, (prev, x) -> prev | x);
        setGauge(currentKey, cpuId);
    }

    public static void bufferGcEvent(long key, String meta) {
        PENDING_GC_EVENTS.add(new GcEvent(key, meta));
    }

    public static List<GcEvent> drainGcEvents() {
        List<GcEvent> batch = new ArrayList<>();
        GcEvent e;
        while ((e = PENDING_GC_EVENTS.poll()) != null) batch.add(e);
        return batch;
    }

    public static Map<Long, AtomicLong> values() { return VALUES; }
}
