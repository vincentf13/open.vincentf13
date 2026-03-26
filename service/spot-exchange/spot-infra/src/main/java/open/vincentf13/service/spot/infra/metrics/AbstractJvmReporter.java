package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * JVM 指標回報器 (抽象基類)
 * 職責：封裝共用的 JVM 內存監控與 GC 監聽邏輯。
 */
@Slf4j
public abstract class AbstractJvmReporter {

    @PostConstruct
    public void init() {
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
                        if (!info.getGcAction().toLowerCase().contains("end of")) return;
                        
                        StaticMetricsHolder.setGauge(getGcDurationKey(), info.getGcInfo().getDuration());
                        StaticMetricsHolder.addCounter(getGcCountKey(), 1);
                    }
                }, null, null);
            }
        }
        log.info("{} initialized with keys: [Used:{}, Max:{}, GcCount:{}, GcDur:{}]", 
                 getClass().getSimpleName(), getUsedMbKey(), getMaxMbKey(), getGcCountKey(), getGcDurationKey());
    }

    @Scheduled(fixedRate = 1000)
    public void reportJvm() {
        Runtime r = Runtime.getRuntime();
        StaticMetricsHolder.setGauge(getUsedMbKey(), (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        StaticMetricsHolder.setGauge(getMaxMbKey(), r.maxMemory() / 1024 / 1024);
        onReport(); // 提供鉤子給子類執行額外邏輯
    }

    protected abstract long getUsedMbKey();
    protected abstract long getMaxMbKey();
    protected abstract long getGcCountKey();
    protected abstract long getGcDurationKey();

    /** 鉤子方法：子類可在此實作服務特有的回報邏輯 (如 TPS 歷史) */
    protected void onReport() {}
}
