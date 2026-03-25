package open.vincentf13.service.spot.infra.metrics;

import open.vincentf13.service.spot.infra.util.Clock;

/**
 * 高性能採樣器 (Sampler)
 * 職責：透過「門鎖 (Gate)」與「時間戳」雙重機制，以極低開銷判定是否應執行統計或採樣。
 * 適合在 Dedicated Thread 的 High-frequency Loop 中使用。
 */
public class Sampler {
    private final int gate;
    private final long intervalMs;
    private long loopCount = 0;
    private long lastTime = 0;

    /**
     * @param gate 門鎖次數 (例如 10000 代表每一萬次循環才檢查一次時間)
     * @param intervalMs 採樣時間間隔 (毫秒)
     */
    public Sampler(int gate, long intervalMs) {
        this.gate = gate;
        this.intervalMs = intervalMs;
    }

    /**
     * 判定目前是否達到採樣時機。
     * 採樣邏輯：先檢查門鎖次數，通過後再檢查時間增量。
     */
    public boolean shouldSample() {
        if (++loopCount >= gate) {
            loopCount = 0;
            long now = Clock.now();
            if (now - lastTime >= intervalMs) {
                lastTime = now;
                return true;
            }
        }
        return false;
    }

    /** 重置採樣器狀態 */
    public void reset() {
        this.loopCount = 0;
        this.lastTime = Clock.now();
    }
}
