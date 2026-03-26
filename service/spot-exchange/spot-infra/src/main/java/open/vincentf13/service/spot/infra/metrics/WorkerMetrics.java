package open.vincentf13.service.spot.infra.metrics;

import open.vincentf13.service.spot.infra.thread.AffinityUtil;

/**
 * 執行緒級別指標 (Worker Metrics)
 * 職責：封裝與目前執行緒強相關的效能指標採集（如 Duty Cycle、CPU 親和性）。
 */
public class WorkerMetrics {

    // --- ThreadLocal Duty Cycle 狀態 ---
    private static final ThreadLocal<long[]> CYCLE_COUNTERS = ThreadLocal.withInitial(() -> new long[2]);

    /** 標記循環開始 */
    public static void startCycle() {
        CYCLE_COUNTERS.get()[0]++;
    }
    
    /** 標記循環結束並記錄是否有工作 */
    public static void endCycle(boolean worked) {
        if (worked) {
            CYCLE_COUNTERS.get()[1]++;
        }
    }

    /** 獲取並重置目前執行緒的有效循環比 (單位: 0.01%) */
    private static long getAndResetDutyCycle() {
        long[] counters = CYCLE_COUNTERS.get();
        if (counters[0] == 0) return 0;
        long res = (counters[1] * 10000) / counters[0];
        counters[0] = 0; counters[1] = 0;
        return res;
    }

    /** 聚合上報目前執行緒效能 (飽和度 + 物理核心 ID) */
    public static void reportThreadMetrics(long cpuIdKey, long loadKey) {
        GaugeMetrics.set(loadKey, getAndResetDutyCycle());
        GaugeMetrics.set(cpuIdKey, AffinityUtil.currentCpu());
    }

    private WorkerMetrics() {}
}
