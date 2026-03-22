package open.vincentf13.service.spot.infra.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 高性能時鐘工具 (Cached Clock)
 * 職責：使用背景執行緒緩存當前時間戳，消除頻繁調用 System.currentTimeMillis() 的開銷。
 */
public class Clock {
    // 使用 volatile 確保多執行緒可見性
    private static volatile long currentMillis = System.currentTimeMillis();

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cached-clock");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY); // 最高優先權確保時間準確
        return t;
    });

    static {
        // 每 1ms 更新一次緩存，這對於交易系統的精度與效能平衡最優
        SCHEDULER.scheduleAtFixedRate(() -> {
            currentMillis = System.currentTimeMillis();
        }, 0, 1, TimeUnit.MILLISECONDS);
    }

    /**
     * 獲取緩存的當前時間戳
     * @return 當前毫秒數
     */
    public static long now() {
        return currentMillis;
    }
}
