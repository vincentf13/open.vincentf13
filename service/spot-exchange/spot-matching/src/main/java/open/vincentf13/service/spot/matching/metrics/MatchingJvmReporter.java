package open.vincentf13.service.spot.matching.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * 撮合服務 JVM 指標回報器
 */
@Component
public class MatchingJvmReporter {

    @PostConstruct
    public void init() {
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
                        if (!info.getGcAction().toLowerCase().contains("end of")) return;
                        StaticMetricsHolder.setGauge(MetricsKey.MATCHING_GC_LAST_DURATION_MS, info.getGcInfo().getDuration());
                        StaticMetricsHolder.addCounter(MetricsKey.MATCHING_GC_COUNT, 1);
                    }
                }, null, null);
            }
        }
    }

    @Scheduled(fixedRate = 1000)
    public void reportJvm() {
        Runtime r = Runtime.getRuntime();
        StaticMetricsHolder.setGauge(MetricsKey.MATCHING_JVM_USED_MB, (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        StaticMetricsHolder.setGauge(MetricsKey.MATCHING_JVM_MAX_MB, r.maxMemory() / 1024 / 1024);
    }
}
