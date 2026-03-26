package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.scheduling.annotation.Scheduled;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JVM 指標回報器 (抽象基類)
 * 職責：封裝共用的 JVM 內存監控與 GC 事件監聽邏輯。
 */
@Slf4j
public abstract class AbstractJvmReporter {
    private static final String GC_META_SEPARATOR = "\u001F";

    private final AtomicInteger gcSlot = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;

                    CompositeData cd = (CompositeData) notification.getUserData();
                    GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
                    reportGcEvent(info);
                }, null, null);
            }
        }
        log.info("{} initialized", getClass().getSimpleName());
    }

    @Scheduled(fixedRate = 1000)
    public void reportJvm() {
        Runtime r = Runtime.getRuntime();
        StaticMetricsHolder.setGauge(getUsedMbKey(), (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        StaticMetricsHolder.setGauge(getMaxMbKey(), r.maxMemory() / 1024 / 1024);
        onReport();
    }

    private synchronized void reportGcEvent(GarbageCollectionNotificationInfo info) {
        long durationMs = info.getGcInfo().getDuration();
        StaticMetricsHolder.addCounter(getGcCountKey(), 1);

        int slot = gcSlot.getAndIncrement() % MetricsKey.GC_HISTORY_MAX_KEEP;
        long eventKey = getGcHistoryStart() + slot;
        long encoded = System.currentTimeMillis() * 1_000_000L + Math.min(durationMs * 1000, 999_999L);

        StaticMetricsHolder.setGauge(eventKey, encoded);
        StaticMetricsHolder.setGauge(getGcDurationKey(), durationMs);
        Storage.self().gcEventHistory().put(eventKey, encodeGcMeta(info.getGcName(), info.getGcAction(), info.getGcCause()));
    }

    private String encodeGcMeta(String gcName, String gcAction, String gcCause) {
        return normalize(gcName) + GC_META_SEPARATOR + normalize(gcAction) + GC_META_SEPARATOR + normalize(gcCause);
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.replace(GC_META_SEPARATOR, " ");
    }

    protected abstract long getUsedMbKey();
    protected abstract long getMaxMbKey();
    protected abstract long getGcCountKey();
    protected abstract long getGcDurationKey();
    protected abstract long getGcHistoryStart();

    protected void onReport() {}
}
