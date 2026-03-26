package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import org.springframework.scheduling.annotation.Scheduled;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JVM 指標回報器 (抽象基類)
 * 職責：封裝共用的 JVM 內存監控與 GC 監聽邏輯。
 */
@Slf4j
public abstract class AbstractJvmReporter {

    // 循環寫入 GC 歷史槽位的計數器
    private final AtomicInteger gcSlot = new AtomicInteger(0);
    // 追蹤每個 GC 管理器的最後一個 GC ID，防止重複記錄同一事件的多個階段
    private final Map<String, Long> lastGcIdMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;
                    CompositeData cd = (CompositeData) notification.getUserData();
                    GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
                    
                    // 1. 去重邏輯：檢查 GC ID 是否已經處理過
                    long gcId = info.getGcInfo().getId();
                    String gcName = info.getGcName();
                    if (lastGcIdMap.getOrDefault(gcName, -1L) == gcId) return;
                    lastGcIdMap.put(gcName, gcId);

                    // 2. 只處理結束通知
                    if (!info.getGcAction().toLowerCase().contains("end of")) return;

                    long durationMs = info.getGcInfo().getDuration();
                    // 編碼：epochMillis × 1000 + durationMicros
                    long encoded = System.currentTimeMillis() * 1_000_000L
                                 + Math.min(durationMs * 1000, 999_999L);

                    int slot = gcSlot.getAndIncrement() % MetricsKey.GC_HISTORY_MAX_KEEP;
                    StaticMetricsHolder.setGauge(getGcHistoryStart() + slot, encoded);
                    StaticMetricsHolder.setGauge(getGcDurationKey(), durationMs);
                    StaticMetricsHolder.addCounter(getGcCountKey(), 1);
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

    protected abstract long getUsedMbKey();
    protected abstract long getMaxMbKey();
    protected abstract long getGcCountKey();
    protected abstract long getGcDurationKey();
    protected abstract long getGcHistoryStart();

    protected void onReport() {}
}
