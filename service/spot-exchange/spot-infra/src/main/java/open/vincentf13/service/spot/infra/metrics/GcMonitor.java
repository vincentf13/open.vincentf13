package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants;
import open.vincentf13.service.spot.infra.util.Clock;

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
    private long lastGcTimestamp = Clock.now();
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

                        String action = info.getGcAction().toLowerCase();
                        if (!action.contains("end of")) return;

                        long now = Clock.now();
                        long interval = now - instance.lastGcTimestamp;
                        long duration = info.getGcInfo().getDuration();

                        if (interval > 500) {
                            MetricsCollector.increment(instance.countKey);
                            MetricsCollector.set(instance.intervalKey, interval);
                            MetricsCollector.set(instance.durationKey, duration);
                            
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
