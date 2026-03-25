package open.vincentf13.service.spot.infra.thread;

import org.agrona.concurrent.IdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 低延遲背景工作者基類 (Worker)
 封裝了基於單執行緒的工作循環模型，採用策略模式平衡延遲與 CPU 消耗。
 職責：提供標準的生命週期管理 (onStart -> doWork -> onStop) 與 CPU 親和性綁定。
 */
public abstract class Worker implements Runnable {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final AtomicBoolean running = new AtomicBoolean(false);
    private int preferredCpuId = -1;

    // 複用全局忙等策略，確保最低延遲
    protected final IdleStrategy idleStrategy = Strategies.BUSY_SPIN;
    private Thread thread;    

    /** 
     啟動工作者執行緒 (帶核心偏好)
     @param name 執行緒名稱
     @param preferredCpuId 優先綁定的核心 ID
     */
    public void start(String name, int preferredCpuId) {
        this.preferredCpuId = preferredCpuId;
        start(name);
    }

    /** 
     啟動工作者執行緒
     @param name 執行緒名稱
     */
    public void start(String name) {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, name);
            thread.start();
            log.info("Worker {} started", name);
        }
    }
    
    /** 
     停止工作者執行緒 (優雅退出)
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        
        if (thread != null) {
            try {
                thread.join(5000);
                if (thread.isAlive()) {
                    log.warn("Worker {} 未能在 5 秒內優雅退出，發出中斷...", thread.getName());
                    thread.interrupt();
                    thread.join(1000);
                    if (thread.isAlive()) {
                        log.error("Worker {} 強制停機超時！執行緒仍活躍。", thread.getName());
                    }
                }
            } catch (InterruptedException e) {
                log.error("等待 Worker {} 停止時被中斷", thread.getName());
                Thread.currentThread().interrupt();
            }
        }
        
        if (thread != null && thread.isAlive()) {
            throw new RuntimeException("Worker " + thread.getName() + " 停機失敗！跳過 onStop 以防 Crash。");
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
                int workDone = doWork();
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
