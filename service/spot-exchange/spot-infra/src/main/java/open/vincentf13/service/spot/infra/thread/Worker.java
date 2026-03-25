package open.vincentf13.service.spot.infra.thread;

import open.vincentf13.service.spot.infra.jvm.Jvm;
import org.agrona.concurrent.IdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 低延遲背景工作者基類 (Worker)
 職責：提供生命週期管理，並透過 Jvm.startCycle/endCycle 接入工作負載監控。
 */
public abstract class Worker implements Runnable {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final AtomicBoolean running = new AtomicBoolean(false);
    private int preferredCpuId = -1;

    protected final IdleStrategy idleStrategy = Strategies.BUSY_SPIN;
    private Thread thread;    

    public void start(String name, int preferredCpuId) {
        this.preferredCpuId = preferredCpuId;
        start(name);
    }

    public void start(String name) {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, name);
            thread.start();
            log.info("Worker {} started", name);
        }
    }
    
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (thread != null) {
            try {
                thread.join(5000);
                if (thread.isAlive()) {
                    log.warn("Worker {} 未能在 5 秒內優雅退出，發出中斷...", thread.getName());
                    thread.interrupt();
                    thread.join(1000);
                }
            } catch (InterruptedException e) {
                log.error("等待 Worker {} 停止時被中斷", thread.getName());
                Thread.currentThread().interrupt();
            }
        }
        if (thread != null && thread.isAlive()) {
            throw new RuntimeException("Worker " + thread.getName() + " 停機失敗！");
        }
        onStop();
        log.info("Worker {} 已停止", thread != null ? thread.getName() : "Unknown");
    }
    
    @Override
    public void run() {
        int boundCpuId = -1;
        try {
            boundCpuId = open.vincentf13.service.spot.infra.util.AffinityUtil.acquireAndBind(preferredCpuId);
        } catch (Exception e) {
            log.warn("CPU Affinity 綁定失敗: {}", e.getMessage());
        }
        onBind(boundCpuId);

        try {
            onStart();
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                Jvm.startCycle();
                int workDone = doWork();
                Jvm.endCycle(workDone > 0);
                
                if (workDone > 0) continue;
                
                Thread.onSpinWait();
                idleStrategy.idle(0);
            }
        } catch (Exception e) {
            if (!(e instanceof InterruptedException) && !(e.getCause() instanceof InterruptedException)) {
                log.error("Worker {} 運行錯誤", Thread.currentThread().getName(), e);
            }
        } finally {
            running.set(false);
        }
    }
    
    protected abstract void onStart();
    protected void onBind(int cpuId) {}
    protected abstract int doWork();
    protected abstract void onStop();
}
