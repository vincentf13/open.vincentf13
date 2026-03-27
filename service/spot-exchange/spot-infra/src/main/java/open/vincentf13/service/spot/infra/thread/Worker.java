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
public abstract class Worker implements Runnable {
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
                    
                    // 每 1024 次循環檢查一次是否該上報指標 (約 1 秒)
                    if ((loopCounter & 0x3FF) == 0) {
                        long now = System.nanoTime();
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
