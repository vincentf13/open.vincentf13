package open.vincentf13.service.spot.infra.thread;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 高性能背景工作者基類 (Worker)
 *
 * 優化策略：
 * 1. 位元遮罩採樣 (Bitmask Sampling)：每 128 次循環才調用一次 nanoTime，開銷降低 99%。
 * 2. 雙路徑循環：分離採樣路徑（cold）與快速路徑（hot），讓 Hot Loop 極度乾淨。
 * 3. 內建 duty cycle 計算：累積工作時間，每秒上報飽和度 (0-100%)。
 */
@Slf4j
@SuppressWarnings("restriction")
public abstract class Worker implements Runnable {
    @jdk.internal.vm.annotation.Contended
    protected final AtomicBoolean running = new AtomicBoolean(false);
    private final String workerName;
    private final long cpuIdKey, currentCpuIdKey, dutyCycleKey;
    private Thread thread;

    private static final int SAMPLE_MASK = 0x7F;   // 每 128 次採樣一次
    private static final IdleStrategy IDLE = new BusySpinIdleStrategy();

    // Duty cycle 採樣狀態 (per-thread, 僅 run() 內使用)
    private long accumulatedWorkNanos;
    private long lastReportNanos;

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

    public void run() {
        AffinityUtil.acquireAndBind();
        onStart();

        lastReportNanos = System.nanoTime();
        long loopCounter = 0;

        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                int work;

                if ((++loopCounter & SAMPLE_MASK) == 0) {
                    // 採樣路徑 (Cold Path)
                    long start = System.nanoTime();
                    work = doWork();
                    long duration = System.nanoTime() - start;

                    if (work > 0) {
                        accumulatedWorkNanos += duration * (SAMPLE_MASK + 1);
                    }

                    // 二級採樣 (每 1024 次)：檢查 1 秒邊界
                    if ((loopCounter & 0x3FF) == 0) {
                        long now = System.nanoTime();
                        if (now - lastReportNanos >= 1_000_000_000L) {
                            reportMetrics(now);
                            lastReportNanos = now;
                        }
                    }
                } else {
                    // 快速路徑 (Hot Path)
                    work = doWork();
                }

                if (work <= 0) IDLE.idle(0);
            }
        } catch (Exception e) {
            if (running.get()) log.error("Worker {} error: {}", workerName, e.getMessage(), e);
        } finally {
            try { onStop(); } catch (Exception ex) { log.error("Worker {} close error", workerName, ex); }
            running.set(false);
        }
    }

    private void reportMetrics(long now) {
        // CPU 綁核追蹤
        StaticMetricsHolder.recordCpuId(cpuIdKey, currentCpuIdKey, AffinityUtil.currentCpu());
        // Duty cycle (0-100%)
        long elapsed = now - lastReportNanos;
        if (elapsed > 0) {
            StaticMetricsHolder.setGauge(dutyCycleKey, Math.min(100, accumulatedWorkNanos * 100 / elapsed));
        }
        accumulatedWorkNanos = 0;
        // 子類擴展點
        onMetricsReport();
    }

    protected abstract void onStart();
    protected abstract int doWork();
    protected abstract void onStop();
    protected void onMetricsReport() {}
}
