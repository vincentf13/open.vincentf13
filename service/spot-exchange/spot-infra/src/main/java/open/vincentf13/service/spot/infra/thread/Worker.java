package open.vincentf13.service.spot.infra.thread;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.metrics.WorkerMetrics;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 高性能背景工作者基類 (Worker)
 職責：管理工作循環，並透過 Sampler 執行低開銷的指標統計。
 */
@Slf4j
public abstract class Worker implements Runnable {
    protected final AtomicBoolean running = new AtomicBoolean(false);
    private final String workerName;
    private final long cpuIdKey, currentCpuIdKey, dutyCycleKey;
    private Thread thread;

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
        if (!running.compareAndSet(true, false))
            return;
        if (thread != null) {
            try {
                thread.join(100);
                if (thread.isAlive())
                    thread.interrupt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("Worker {} stopped", thread.getName());
        }
    }

    /**
     核心執行循環：
     1. 綁定 CPU 親和性 (Affinity) 以減少上下文切換。
     2. 呼叫 onStart() 進行資源初始化。
     3. 進入主循環，執行任務 (doWork) 並統計任務週期 (WorkerMetrics)。
     4. 每秒固定觸發一次指標回報 (collectMetrics)。
     5. 若當前無任務 (work <= 0)，執行 Busy-Spin 等待策略。
     6. 異常處理與 finally 資源回收 (onStop)。
     */
    public void run() {
        // 綁定當前執行緒到指定 CPU 核心 (若環境支援)
        AffinityUtil.acquireAndBind();
        long lastMetricsNs = System.nanoTime();
        try {
            onStart();
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                // 開始統計當前任務週期
                WorkerMetrics.startCycle();

                // 執行具體業務邏輯，並獲取處理量
                int work = doWork();

                // 結束週期統計 (work > 0 視為有效週期)
                WorkerMetrics.endCycle(work);

                // 每秒定期執行一次自定義指標收集
                if (System.nanoTime() - lastMetricsNs >= 1_000_000_000L) {
                    collectMetrics();
                    lastMetricsNs = System.nanoTime();
                }

                // 若無任務，執行 Busy-Spin 策略以降低 CPU 負擔並保持低延遲回報
                if (work <= 0)
                    Strategies.BUSY_SPIN.idle(0);
            }
        } catch (Exception e) {
            if (running.get())
                log.error("Worker 運行異常: {}", e.getMessage(), e);
        } finally {
            // 確保資源釋放並標記為已停止
            try {
                onStop();
            } catch (Exception ex) {
                log.error("Worker 關閉異常", ex);
            }
            running.set(false);
        }
    }

    protected abstract void onStart();

    protected abstract int doWork();

    /**
     專屬統計方法：每秒觸發一次，用於回報效能指標
     */
    protected void collectMetrics() {
        StaticMetricsHolder.recordCpuId(cpuIdKey, currentCpuIdKey, AffinityUtil.currentCpu());
        WorkerMetrics.reportDutyCycle(dutyCycleKey);
    }

    protected abstract void onStop();
}
