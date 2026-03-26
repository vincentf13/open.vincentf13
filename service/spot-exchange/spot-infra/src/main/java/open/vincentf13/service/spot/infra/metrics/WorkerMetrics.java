package open.vincentf13.service.spot.infra.metrics;

import open.vincentf13.service.spot.infra.thread.AffinityUtil;

/**
 * 執行緒級別指標 (位圖版)
 */
public class WorkerMetrics {
    public static void reportThreadMetrics(long cpuIdKey, long loadKey) {
        // 紀錄執行緒曾使用過的所有 CPU ID (Bitmask)
        StaticMetricsHolder.recordCpuId(cpuIdKey, AffinityUtil.currentCpu());
    }
    
    public static void startCycle() {}
    public static void endCycle(boolean worked) {}
}
