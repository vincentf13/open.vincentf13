package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants;
import open.vincentf13.service.spot.infra.util.Clock;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

import static open.vincentf13.service.spot.infra.Constants.MetricsKey.*;

/**
 * GC 事件監聽器 (GC Listener)
 * 職責：監聽 JVM GC 通知並記錄次數、間隔與耗時指標。
 */
@Slf4j
@Component
public class GcListener {

    private final ApplicationContext ctx;
    private long lastGcTimestamp = Clock.now();
    private long gcLocalCount = 0;

    // 指標 Key 配置
    private long kGcCount, kGcInterval, kGcDuration, kGcHistory;

    public GcListener(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @PostConstruct
    public void init() {
        var beans = ctx.getBeansWithAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class);
        if (beans.isEmpty()) return;
        String mainClassName = beans.values().iterator().next().getClass().getSimpleName();

        if (mainClassName.contains("WsApi")) {
            setupKeys(GATEWAY_GC_COUNT, GATEWAY_GC_LAST_INTERVAL_MS, GATEWAY_GC_LAST_DURATION_MS, GATEWAY_GC_HISTORY_START);
        } else if (mainClassName.contains("Matching")) {
            setupKeys(MATCHING_GC_COUNT, MATCHING_GC_LAST_INTERVAL_MS, MATCHING_GC_LAST_DURATION_MS, MATCHING_GC_HISTORY_START);
        } else {
            return;
        }

        startListening();
    }

    private void setupKeys(long gcc, long gci, long gcd, long gch) {
        this.kGcCount = gcc; this.kGcInterval = gci; this.kGcDuration = gcd; this.kGcHistory = gch;
    }

    private void startListening() {
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
                        if (!info.getGcAction().toLowerCase().contains("end of")) return;

                        long now = Clock.now(), interval = now - lastGcTimestamp, duration = info.getGcInfo().getDuration();
                        if (interval > 500) {
                            CounterMetrics.increment(kGcCount);
                            GaugeMetrics.set(kGcInterval, interval);
                            GaugeMetrics.set(kGcDuration, duration);
                            long slot = kGcHistory - (++gcLocalCount % Constants.MetricsKey.GC_HISTORY_MAX_KEEP);
                            GaugeMetrics.set(slot, now);
                            lastGcTimestamp = now;
                        }
                    }
                }, null, null);
            }
        }
        log.info("GcListener started.");
    }
}
