package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import lombok.extern.slf4j.Slf4j;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * JVM GC 監測器
 * 利用 JMX 異步監聽 GC 事件，計算間隔與耗時並上報指標。
 */
@Slf4j
public class GcMonitor {
    private static long lastGcTimestamp = System.currentTimeMillis();

    /**
     * 啟動 GC 監聽
     * @param countKey 總次數 Key
     * @param intervalKey 間隔時間 Key
     * @param durationKey 耗時 Key
     */
    public static void start(long countKey, long intervalKey, long durationKey) {
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);

                        long now = System.currentTimeMillis();
                        long interval = now - lastGcTimestamp;
                        long duration = info.getGcInfo().getDuration();

                        // 排除間隔過短的極小 GC (例如部分回收)
                        if (interval > 10) {
                            MetricsCollector.increment(countKey);
                            MetricsCollector.set(intervalKey, interval);
                            MetricsCollector.set(durationKey, duration);
                            lastGcTimestamp = now;

                            log.info("[GC-MONITOR] Type: {}, Action: {}, Duration: {}ms, Interval: {}ms",
                                    info.getGcName(), info.getGcAction(), duration, interval);
                        }
                    }
                }, null, null);
            }
        }
        log.info("GC Monitor started for keys: {}, {}, {}", countKey, intervalKey, durationKey);
    }
}
