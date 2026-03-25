package open.vincentf13.service.spot.infra.thread;

import open.vincentf13.service.spot.infra.jvm.Jvm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/** 
 高性能背景工作者基類 (Worker)
 職責：管理單執行緒工作循環，支持自動 CPU 綁核與 Duty Cycle 監控。
 */
public abstract class Worker implements Runnable {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    /** 啟動工作者，自動分配核心 */
    public void start(String name) {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, name);
            thread.start();
            log.info("Worker {} started", name);
        }
    }

    public void stop() {
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
        onBind(AffinityUtil.acquireAndBind());
        try {
            onStart();
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                Jvm.startCycle();
                int work = doWork();
                Jvm.endCycle(work > 0);
                if (work <= 0) { Thread.onSpinWait(); Strategies.BUSY_SPIN.idle(0); }
            }
        } catch (Exception e) { if (!Thread.interrupted()) log.error("Worker 運行異常", e); }
        finally { running.set(false); }
    }

    protected abstract void onStart();
    protected void onBind(int cpuId) {}
    protected abstract int doWork();
    protected abstract void onStop();
}
