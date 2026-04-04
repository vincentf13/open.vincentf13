package open.vincentf13.service.spot.infra.metrics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指標靜態門面
 * 直接持有 AtomicLong Map，消除 Spring 初始化時序問題。
 * 延遲指標使用 lock-free 環形緩衝區，消除 synchronized 與 array resize GC。
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

    /**
     * Lock-free 環形延遲採樣窗口
     *
     * record() 由單一 hot-path 線程調用 (matching/gateway worker)，無鎖。
     * snapshotAndReset() 由 metrics 上報線程每秒調用一次。
     * 透過 volatile writePos 保證跨線程可見性，消除 synchronized。
     * 固定 64K slots 預分配，消除 array resize GC。
     */
    static class LatencyWindow {
        private static final int CAPACITY = 1 << 16; // 65536
        private static final int MASK = CAPACITY - 1;
        private final long[] ring = new long[CAPACITY];
        private volatile int writePos = 0;
        private int snapshotPos = 0; // 僅 snapshot 線程寫入

        void record(long nanos) {
            ring[writePos & MASK] = nanos;
            writePos++; // volatile write，確保 snapshot 線程可見
        }

        LatencySnapshot snapshotAndReset() {
            int wp = writePos;
            int count = wp - snapshotPos;
            if (count <= 0) return null;
            if (count > CAPACITY) {
                // 溢出：僅取最近 CAPACITY 筆
                snapshotPos = wp - CAPACITY;
                count = CAPACITY;
            }

            long[] copy = new long[count];
            for (int i = 0; i < count; i++) {
                copy[i] = ring[(snapshotPos + i) & MASK];
            }
            snapshotPos = wp;

            Arrays.sort(copy);
            return new LatencySnapshot(
                    percentile(copy, 0.50d),
                    percentile(copy, 0.99d),
                    copy[copy.length - 1]);
        }

        private static long percentile(long[] sorted, double ratio) {
            int index = (int) Math.ceil(sorted.length * ratio) - 1;
            return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
        }
    }
}
