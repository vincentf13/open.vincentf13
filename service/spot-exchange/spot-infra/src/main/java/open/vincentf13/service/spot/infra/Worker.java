package open.vincentf13.service.spot.infra;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
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
    // 預設採用 Backoff 策略，在無工作時依次進行自旋、Yield 與暫停，以減少 CPU 空轉壓力
    protected final IdleStrategy idleStrategy = new BackoffIdleStrategy(1, 1, 1, 1);
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
     設置運行狀態為 false 並嘗試等待執行緒結束，隨後觸發 onStop 鉤子
     */
    public void stop() {
        running.set(false);
        if (thread != null) {
            try {
                thread.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
        onStop();
    }
    
    /** 
     工作者運行核心
     定義了完整的生命週期：onStart -> 循環執行 doWork -> 結束時觸發
     */
    @Override
    public void run() {
        onStart();
        while (running.get()) {
            // doWork 返回處理的任務數量，供 IdleStrategy 決定是否需要進入空閒等待
            int workDone = doWork();
            idleStrategy.idle(workDone);
        }
    }
    
    /** 啟動時的初始化鉤子，由子類實作 */
    protected abstract void onStart();
    
    /** 
     核心工作邏輯
     @return 本次處理的任務數。返回 0 代表無任務，將觸發 IdleStrategy 的空閒邏輯
     */
    protected abstract int doWork();
    
    /** 停止時的資源清理鉤子，由子類實作 */
    protected abstract void onStop();
}
