package open.vincentf13.service.spot.infra.metrics;

import open.vincentf13.service.spot.infra.thread.AffinityUtil;

/**
 * 工作執行緒監控指標 (Worker Metrics) - 高性能採樣版
 * 職責：透過採樣累加，降低 System.nanoTime() 對高性能循環的干擾。
 */
public class WorkerMetrics {

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private static class State {
        long accumulatedWorkNanos; 
        long lastReportNanos = System.nanoTime(); 
    }

    /** 
     * 紀錄一筆採樣數據。
     * @param nanos 該次採樣消耗的納秒
     * @param sampleInterval 採樣間隔 (用於放大結果)
     */
    public static void recordSample(long nanos, int sampleInterval) {
        STATE.get().accumulatedWorkNanos += (nanos * sampleInterval);
    }

    /** 
     * 計算並回報飽和度 (Duty Cycle)。
     * @param loadKey 存放飽和度 (Duty Cycle 0-100) 的指標 Key
     */
    public static void reportDutyCycle(long loadKey) {
        State s = STATE.get();
        long now = System.nanoTime();
        
        long totalElapsed = now - s.lastReportNanos;
        if (totalElapsed > 0) {
            // 計算工作佔比百分比 (0-100)
            long load = (s.accumulatedWorkNanos * 100) / totalElapsed;
            StaticMetricsHolder.setGauge(loadKey, Math.min(100, load));
        }
        
        s.accumulatedWorkNanos = 0;
        s.lastReportNanos = now;
    }
}
