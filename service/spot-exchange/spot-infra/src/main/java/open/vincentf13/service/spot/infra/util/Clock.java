package open.vincentf13.service.spot.infra.util;

/**
 * 高性能緩存時鐘 (Cached Clock)
 * 職責：減少 System.currentTimeMillis() 的系統調用開銷。
 * 採用背景執行緒每毫秒更新一次緩存值，適用於對時間精度要求在 1ms 內的場景。
 */
public class Clock {

    private static volatile long currentMillis = System.currentTimeMillis();

    static {
        Thread clockThread = new Thread(() -> {
            while (true) {
                currentMillis = System.currentTimeMillis();
                try {
                    // 使用睡眠以減少 CPU 佔用，雖然不絕對精準，但對指標統計與超時判斷已足夠
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "cached-clock");
        clockThread.setDaemon(true);
        clockThread.start();
    }

    /** 獲取緩存的目前時間 (毫秒) */
    public static long now() {
        return currentMillis;
    }

    private Clock() {}
}
