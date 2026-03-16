package open.vincentf13.service.spot.infra;

import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import net.openhft.affinity.AffinityLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 低延遲背景工作者基類
 封裝了基於單執行緒的工作循環模型，並採用 Agrona 的 IdleStrategy 以平衡延遲與 CPU 消耗
 適用於 Gateway、Matching Engine 等需要高效能處理的組件
 */
public abstract class Worker implements Runnable {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final AtomicBoolean running = new AtomicBoolean(false);

    // 改用 BusySpinIdleStrategy，拒絕任何讓出，確保最低延遲
    protected final IdleStrategy idleStrategy = new BusySpinIdleStrategy();
    private Thread thread;    
    /** 
     啟動工作者執行緒
     @param name 執行緒名稱，便於監控與診斷
     */
    public void start(String name) {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, name);
            thread.start();
            log.info("Worker {} started", name);
        }
    }
    
    /** 
     停止工作者執行緒
     採用「優雅退出」策略：先發出停止信號，等待執行緒結束後再執行資源清理
     */
    public void stop() {
        // 確保停止邏輯只執行一次
        if (!running.compareAndSet(true, false)) {
            return;
        }
        
        if (thread != null) {
            try {
                // 1. 給予較充裕的時間（5秒）等待背景執行緒完成當前任務並退出循環
                thread.join(5000);
                if (thread.isAlive()) {
                    log.warn("Worker {} 未能在 5 秒內優雅退出，嘗試發出中斷信號...", thread.getName());
                    // 2. 若超時仍未退出，發出中斷信號以喚醒可能處於阻塞狀態的操作
                    thread.interrupt();
                    // 3. 最後再給予 1 秒的緩衝時間
                    thread.join(1000);
                    if (thread.isAlive()) {
                        log.error("Worker {} 強制停機超時！執行緒仍處於活躍狀態，這可能導致釋放堆外內存時發生 JVM Crash", thread.getName());
                    }
                }
            } catch (InterruptedException e) {
                log.error("等待 Worker {} 停止時被中斷", thread.getName());
                Thread.currentThread().interrupt();
            }
        }
        
        // 狀態二次確認：如果在所有等待手段後執行緒仍然活躍
        if (thread != null && thread.isAlive()) {
            String errorMsg = String.format("Worker %s 停機失敗且仍然處於活躍狀態！為了預防 JVM Crash，已跳過資源釋放 (onStop)。", thread.getName());
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        
        // 執行資源清理（如釋放 Bytes、關閉 Publication 等）
        onStop();
        log.info("Worker {} 已停止並清理資源", thread != null ? thread.getName() : "Unknown");
    }
    
    /** 
     工作者運行核心
     定義了完整的生命週期：onStart -> 循環執行 doWork -> 結束
     */
    @Override
    public void run() {
        // 在進程允許的核心範圍內自動分配並綁定物理核心 (軟硬結合)
        int boundCpuId = open.vincentf13.service.spot.infra.util.AffinityUtil.acquireAndBind();
        onBind(boundCpuId);

        try {
            onStart();
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                int workDone = doWork();
                // 根據工作量決定等待策略：有任務時不休眠，無任務時逐步退避 (Spin -> Yield -> Park)
                idleStrategy.idle(workDone);
            }
        } catch (Exception e) {
            // 捕獲所有異常，確保執行緒崩潰時有日誌記錄
            if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
                log.warn("Worker {} 收到中斷信號，準備退出", Thread.currentThread().getName());
            } else {
                log.error("Worker {} 運行時發生未預期錯誤", Thread.currentThread().getName(), e);
            }
        } finally {
            // 確保循環結束，便於 stop() 中的 join() 順利返回
            running.set(false);
        }
    }
    
    /** 
      啟動時的初始化鉤子，由子類實作
      此方法在背景工作執行緒正式開始 while 循環前被調用一次，
      適用於建立網絡連線、加載 Checkpoint 或初始化內存索引等操作
     */
    protected abstract void onStart();
    
    /** 
      當執行緒成功鎖定 CPU 核心時觸發
      @param cpuId 鎖定的物理核心 ID
     */
    protected void onBind(int cpuId) {}

    /** 
     核心工作邏輯
     @return 本次處理的任務數。返回 0 代表無任務，將觸發 IdleStrategy 的空閒邏輯
     */
    protected abstract int doWork();
    
    /** 停止時的資源清理鉤子，由子類實作 */
    protected abstract void onStop();
}
