package open.vincentf13.service.spot.infra.metrics;

import org.agrona.collections.LongArrayList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指標靜態門面
 * 直接持有 AtomicLong Map，消除 Spring 初始化時序問題。
 * 延遲指標使用秒級窗口統計，避免累積快照污染當前觀測。
 */
public class StaticMetricsHolder {

    private static final ConcurrentHashMap<Long, AtomicLong> VALUES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, LatencyWindow> LATENCY_WINDOWS = new ConcurrentHashMap<>();

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

    public static void recordLatency(long key, long nanos) {
        LATENCY_WINDOWS.computeIfAbsent(key, k -> new LatencyWindow()).record(nanos);
    }

    public static Map<Long, AtomicLong> values() { return VALUES; }

    public static Map<Long, LatencySnapshot> snapshotLatencyAndReset() {
        Map<Long, LatencySnapshot> snapshots = new HashMap<>();
        LATENCY_WINDOWS.forEach((key, window) -> {
            LatencySnapshot snapshot = window.snapshotAndReset();
            if (snapshot != null) {
                snapshots.put(key, snapshot);
            }
        });
        return snapshots;
    }

    public record LatencySnapshot(long p50, long p99, long max) {}

    private static class LatencyWindow {
        private final LongArrayList samples = new LongArrayList();

        private synchronized void record(long nanos) {
            samples.add(nanos);
        }

        private synchronized LatencySnapshot snapshotAndReset() {
            if (samples.isEmpty()) {
                return null;
            }

            long[] values = samples.toLongArray();
            samples.clear();
            Arrays.sort(values);

            return new LatencySnapshot(
                percentile(values, 0.50d),
                percentile(values, 0.99d),
                values[values.length - 1]
            );
        }

        private long percentile(long[] values, double ratio) {
            int index = (int) Math.ceil(values.length * ratio) - 1;
            if (index < 0) {
                index = 0;
            } else if (index >= values.length) {
                index = values.length - 1;
            }
            return values[index];
        }
    }
}
