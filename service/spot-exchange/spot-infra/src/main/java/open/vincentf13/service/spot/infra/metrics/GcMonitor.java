package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import lombok.extern.slf4j.Slf4j;

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
    private final long countKey;
    private final long intervalKey;
    private final long durationKey;

    private GcMonitor(long countKey, long intervalKey, long durationKey) {
        this.countKey = countKey;
        this.intervalKey = intervalKey;
        this.durationKey = durationKey;
    }

    /**
     * 啟動 GC 監聽
     */
    public static void start(long countKey, long intervalKey, long durationKey) {
        GcMonitor instance = new GcMonitor(countKey, intervalKey, durationKey);
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);

                        long now = System.currentTimeMillis();
                        long interval = now - instance.lastGcTimestamp;
                        long duration = info.getGcInfo().getDuration();

                        // 排除間隔過短的極小 GC
                        if (interval > 10) {
                            MetricsCollector.increment(instance.countKey);
                            MetricsCollector.set(instance.intervalKey, interval);
                            MetricsCollector.set(instance.durationKey, duration);
                            instance.lastGcTimestamp = now;

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
