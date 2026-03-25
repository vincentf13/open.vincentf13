package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants;
import open.vincentf13.service.spot.infra.chronicle.Storage;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * JVM GC 監測器 (實例化版)
 * 利用 JMX 異步監聽 GC 事件，計算間隔與耗時並上報指標。
 */
@Slf4j
public class GcMonitor {
    private long lastGcTimestamp = System.currentTimeMillis();
    private long localCount = 0; // 內部精確計數器，用於槽位計算
    private final long countKey;
    private final long intervalKey;
    private final long durationKey;
    private final long historyStartKey;

    private GcMonitor(long countKey, long intervalKey, long durationKey, long historyStartKey) {
        this.countKey = countKey;
        this.intervalKey = intervalKey;
        this.durationKey = durationKey;
        this.historyStartKey = historyStartKey;
    }

    /**
     * 啟動 GC 監聽
     */
    public static void start(long countKey, long intervalKey, long durationKey, long historyStartKey) {
        GcMonitor instance = new GcMonitor(countKey, intervalKey, durationKey, historyStartKey);
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);

                        // 過濾：只處理 "end of" 的通知，避免 ZGC/G1 多階段導致的重複計算
                        String action = info.getGcAction().toLowerCase();
                        if (!action.contains("end of")) {
                            return;
                        }

                        long now = System.currentTimeMillis();
                        long interval = now - instance.lastGcTimestamp;
                        long duration = info.getGcInfo().getDuration();

                        // 排除過於頻繁的干擾訊號 (合併 500ms 內的通知)
                        if (interval > 500) {
                            MetricsCollector.increment(instance.countKey);
                            MetricsCollector.set(instance.intervalKey, interval);
                            MetricsCollector.set(instance.durationKey, duration);
                            
                            // 使用內部精確計數來決定槽位，解決併發覆蓋問題
                            long currentCount = ++instance.localCount;
                            long historySlot = instance.historyStartKey - (currentCount % Constants.MetricsKey.GC_HISTORY_MAX_KEEP);
                            MetricsCollector.set(historySlot, now);
                            
                            instance.lastGcTimestamp = now;

                            log.info("[GC-MONITOR] Name: {}, Action: {}, Duration: {}ms, Interval: {}ms",
                                    info.getGcName(), info.getGcAction(), duration, interval);
                        }
                    }
                }, null, null);
            }
        }
        log.info("GC Monitor started for keys: {}, {}, {}, history: {}", 
                countKey, intervalKey, durationKey, historyStartKey);
    }
}
