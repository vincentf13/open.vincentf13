package open.vincentf13.service.spot.infra.metrics;

import open.vincentf13.service.spot.infra.thread.AffinityUtil;

/**
 * 執行緒級別指標 (簡化版)
 */
public class WorkerMetrics {
    public static void reportThreadMetrics(long cpuIdKey, long loadKey) {
        // 暫時移除複雜的 Duty Cycle 計算，僅上報 CPU ID
        StaticMetricsHolder.setGauge(cpuIdKey, AffinityUtil.currentCpu());
    }
    
    // 保留空方法避免編譯錯誤，之後再加回簡化邏輯
    public static void startCycle() {}
    public static void endCycle(boolean worked) {}
}
