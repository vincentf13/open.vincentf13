package open.vincentf13.service.spot.infra.metrics;

import open.vincentf13.service.spot.infra.thread.AffinityUtil;

/**
 * 工作執行緒監控指標 (Worker Metrics)
 * 職責：在高性能場景下，精確監控執行緒的「CPU 綁核狀態」與「任務飽和度 (Duty Cycle)」。
 * 
 * 飽和度定義：執行緒實際執行業務邏輯的時間 / 總觀測時間。
 * 這解決了 Busy-spin 執行緒在 OS 層面永遠顯示 100% CPU 佔用，導致無法區分「真忙」與「空轉」的問題。
 */
public class WorkerMetrics {

    // 使用 ThreadLocal 隔離各執行緒數據，實現零鎖競爭、零對象分配的極速統計
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private static class State {
        long cycleStartNanos;      // 當前循環開始時間
        long accumulatedWorkNanos; // 在回報週期內累計的「實際工作」納秒數
        long lastReportNanos = System.nanoTime(); // 上次上報指標的時間
    }

    /** 
     * 標記一個處理循環的開始。
     * 通常在 Worker 迴圈的最頂端呼叫。
     */
    public static void startCycle() {
        STATE.get().cycleStartNanos = System.nanoTime();
    }

    /** 
     * 標記一個處理循環的結束。
     * @param worked 本次循環是否處理了實際任務 (如果有 Match、有 Order 則為 true)
     */
    public static void endCycle(boolean worked) {
        if (worked) {
            State s = STATE.get();
            // 只有在真正處理了任務時，才將消耗的時間計入「有效工作時間」
            s.accumulatedWorkNanos += (System.nanoTime() - s.cycleStartNanos);
        }
    }

    /** 
     * 計算並回報執行緒指標。
     * @param cpuIdKey 存放 CPU 綁核位圖 (Affinity Bitmask) 的指標 Key
     * @param loadKey 存放飽和度 (Duty Cycle 0-100) 的指標 Key
     */
    public static void reportThreadMetrics(long cpuIdKey, long loadKey) {
        State s = STATE.get();
        long now = System.nanoTime();
        
        // 1. 紀錄 CPU 親和力狀態 (利用位圖追蹤執行緒是否發生過跳核)
        StaticMetricsHolder.recordCpuId(cpuIdKey, AffinityUtil.currentCpu());
        
        // 2. 計算飽和度 (Duty Cycle)
        long totalElapsed = now - s.lastReportNanos;
        if (totalElapsed > 0) {
            // 計算工作佔比百分比 (0-100)
            long load = (s.accumulatedWorkNanos * 100) / totalElapsed;
            StaticMetricsHolder.setGauge(loadKey, Math.min(100, load));
        }
        
        // 重置統計窗口，開始下一個觀測週期
        s.accumulatedWorkNanos = 0;
        s.lastReportNanos = now;
    }
}
