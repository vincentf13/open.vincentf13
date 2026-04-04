package open.vincentf13.service.spot.infra.thread;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.metrics.WorkerMetrics;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 高性能背景工作者基類 (Worker)
 * 優化策略：
 * 1. 位元遮罩採樣 (Bitmask Sampling)：每 128 次循環才調用一次 nanoTime，開銷降低 99%。
 * 2. 雙路徑循環：分離採樣路徑與快速路徑，讓 Hot Loop 極度乾淨。
 */
@Slf4j
@SuppressWarnings("restriction")
public abstract class Worker implements Runnable {
    /** 控制線程 (start/stop) 與工作線程 (busy-spin loop) 共享，@Contended 防止偽共享 */
    @jdk.internal.vm.annotation.Contended
    protected final AtomicBoolean running = new AtomicBoolean(false);
    private final String workerName;
    private final long cpuIdKey, currentCpuIdKey, dutyCycleKey;
    private Thread thread;

    // 採樣遮罩：每 128 次循環 (2^7) 採樣一次
    private static final int SAMPLE_MASK = 0x7F;

    protected Worker(String workerName, long cpuIdKey, long currentCpuIdKey, long dutyCycleKey) {
        this.workerName = workerName;
        this.cpuIdKey = cpuIdKey;
        this.currentCpuIdKey = currentCpuIdKey;
        this.dutyCycleKey = dutyCycleKey;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, workerName);
            thread.start();
            log.info("Worker {} started", workerName);
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (thread != null) {
            try {
                thread.join(100);
                if (thread.isAlive()) thread.interrupt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("Worker {} stopped", thread.getName());
        }
    }

    /**
     * 核心執行循環 (極簡採樣版)
     */
    public void run() {
        AffinityUtil.acquireAndBind();
        onStart();
        
        long lastReportNs = System.nanoTime();
        long loopCounter = 0;

        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                int work;
                
                // 採樣路徑 (Sample Path / Cold Path)
                if ((++loopCounter & SAMPLE_MASK) == 0) {
                    long start = System.nanoTime();
                    work = doWork();
                    long duration = System.nanoTime() - start;
                    
                    if (work > 0) {
                        WorkerMetrics.recordSample(duration, SAMPLE_MASK + 1);
                    }
                    
                    // 二級採樣檢查 (每 1024 次循環，約 1-10ms 頻率)：
                    // 為了極致性能，我們不希望在每次 128 次採樣時都去判斷 1 秒上報條件。
                    // 透過 & 0x3FF (1023) 遮罩，我們進一步將「看錶」的次數降低到原本的 1/8。
                    if ((loopCounter & 0x3FF) == 0) {
                        long now = System.nanoTime();
                        // 最終檢查：若已跨越 1 秒邊界，執行指標上報與重置
                        if (now - lastReportNs >= 1_000_000_000L) {
                            StaticMetricsHolder.recordCpuId(cpuIdKey, currentCpuIdKey, AffinityUtil.currentCpu());
                            WorkerMetrics.reportDutyCycle(dutyCycleKey);
                            onMetricsReport();
                            lastReportNs = now;
                        }
                    }

                } else {
                    // 快速路徑 (Fast Path / Hot Path) - 幾乎零額外開銷
                    work = doWork();
                }

                if (work <= 0) {
                    Strategies.BUSY_SPIN.idle(0);
                }
            }
        } catch (Exception e) {
            if (running.get()) log.error("Worker {} 異常: {}", workerName, e.getMessage(), e);
        } finally {
            try { onStop(); } catch (Exception ex) { log.error("Worker {} 關閉異常", workerName, ex); }
            running.set(false);
        }
    }

    protected abstract void onStart();
    protected abstract int doWork();
    protected abstract void onStop();
    /** 選用的指標回報掛勾 (每秒調用一次) */
    protected void onMetricsReport() {}
}
