package open.vincentf13.service.spot.infra.thread;

import lombok.extern.slf4j.Slf4j;
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
    private Thread thread;
    
    protected Worker(String workerName) {
        this.workerName = workerName;
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
    
    
    public void run() {
        AffinityUtil.acquireAndBind();
        long lastMetricsNs = System.nanoTime();
        try {
            onStart();
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                WorkerMetrics.startCycle();
                int work = doWork();
                WorkerMetrics.endCycle(work > 0);
                
                if (System.nanoTime() - lastMetricsNs >= 1_000_000_000L) {
                    collectMetrics();
                    lastMetricsNs = System.nanoTime();
                }
                
                if (work <= 0)
                    Strategies.BUSY_SPIN.idle(0);
            }
        } catch (Exception e) {
            if (running.get())
                log.error("Worker 運行異常", e);
        } finally {
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
    }
    
    protected abstract void onStop();
}
