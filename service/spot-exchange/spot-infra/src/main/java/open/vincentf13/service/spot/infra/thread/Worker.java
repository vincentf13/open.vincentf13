package open.vincentf13.service.spot.infra.thread;

import open.vincentf13.service.spot.infra.metrics.Sampler;
import open.vincentf13.service.spot.infra.metrics.WorkerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/** 
 高性能背景工作者基類 (Worker)
 職責：管理工作循環，並透過 Sampler 執行低開銷的指標統計。
 */
public abstract class Worker implements Runnable {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;
    private final Sampler metricsSampler = new Sampler(10000, 1000);

    public void workerStart(String name) {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, name);
            thread.start();
            log.info("Worker {} started", name);
        }
    }

    public void workerStop() {
        if (!running.compareAndSet(true, false) || thread == null) return;
        try {
            thread.join(5000);
            if (thread.isAlive()) { thread.interrupt(); thread.join(1000); }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (thread.isAlive()) log.error("Worker {} 停機失敗", thread.getName());
        else { onStop(); log.info("Worker {} stopped", thread.getName()); }
    }

    @Override
    public void run() {
        AffinityUtil.acquireAndBind();
        metricsSampler.reset();
        try {
            onStart();
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                WorkerMetrics.startCycle();
                int work = doWork();
                WorkerMetrics.endCycle(work > 0);

                if (metricsSampler.shouldSample()) collectMetrics();

                if (work <= 0) { Thread.onSpinWait(); Strategies.BUSY_SPIN.idle(0); }
            }
        } catch (Exception e) { if (!Thread.interrupted()) log.error("Worker 運行異常", e); }
        finally { running.set(false); }
    }

    protected abstract void onStart();
    protected abstract int doWork();
    /** 專屬統計方法：每秒觸發一次，用於回報效能指標 */
    protected void collectMetrics() {}
    protected abstract void onStop();
}
