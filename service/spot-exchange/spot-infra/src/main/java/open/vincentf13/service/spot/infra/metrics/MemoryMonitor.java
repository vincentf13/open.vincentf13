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
import java.lang.management.OperatingSystemMXBean;

import static open.vincentf13.service.spot.infra.Constants.MetricsKey.*;

/**
 * 自動化 JVM 監測器 (JvmMonitor)
 * 職責：自動識別服務類型並啟動 GC 監聽與定時指標採集。
 */
@Slf4j
@Component
public class MemoryMonitor {

    private final ApplicationContext ctx;
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getOperatingSystemMXBean();
    
    private long lastGcTimestamp = Clock.now();
    private long gcLocalCount = 0;

    // --- 指標 Key 配置 ---
    private long kUsed, kMax, kLoad, kGcCount, kGcInterval, kGcDuration, kGcHistory;

    public MemoryMonitor(ApplicationContext ctx) { this.ctx = ctx; }

    @PostConstruct
    public void init() {
        String mainClassName = ctx.getBeansWithAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class)
                .values().iterator().next().getClass().getSimpleName();

        if (mainClassName.contains("WsApi")) {
            setupKeys(GATEWAY_JVM_USED_MB, GATEWAY_JVM_MAX_MB, GATEWAY_AERON_SENDER_WORKER_DUTY_CYCLE,
                      GATEWAY_GC_COUNT, GATEWAY_GC_LAST_INTERVAL_MS, GATEWAY_GC_LAST_DURATION_MS, GATEWAY_GC_HISTORY_START);
        } else if (mainClassName.contains("Matching")) {
            setupKeys(MATCHING_JVM_USED_MB, MATCHING_JVM_MAX_MB, MATCHING_AERON_RECEVIER_WORKER_DUTY_CYCLE,
                      MATCHING_GC_COUNT, MATCHING_GC_LAST_INTERVAL_MS, MATCHING_GC_LAST_DURATION_MS, MATCHING_GC_HISTORY_START);
        } else {
            log.warn("Unknown app type: {}, skipping JvmMonitor", mainClassName);
            return;
        }

        startMonitoring();
    }

    private void setupKeys(long used, long max, long load, long gcc, long gci, long gcd, long gch) {
        this.kUsed = used; this.kMax = max; this.kLoad = load;
        this.kGcCount = gcc; this.kGcInterval = gci; this.kGcDuration = gcd; this.kGcHistory = gch;
    }

    private void startMonitoring() {
        // 1. GC 監聽
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
                        if (!info.getGcAction().toLowerCase().contains("end of")) return;

                        long now = Clock.now(), interval = now - lastGcTimestamp, duration = info.getGcInfo().getDuration();
                        if (interval > 500) {
                            MetricsCollector.increment(kGcCount);
                            MetricsCollector.set(kGcInterval, interval);
                            MetricsCollector.set(kGcDuration, duration);
                            long slot = kGcHistory - (++gcLocalCount % Constants.MetricsKey.GC_HISTORY_MAX_KEEP);
                            MetricsCollector.set(slot, now);
                            lastGcTimestamp = now;
                        }
                    }
                }, null, null);
            }
        }

        // 2. 定時採樣線程
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    MetricsCollector.set(kUsed, (RUNTIME.totalMemory() - RUNTIME.freeMemory()) / 1024 / 1024);
                    MetricsCollector.set(kMax, RUNTIME.maxMemory() / 1024 / 1024);
                    if (OS_BEAN instanceof com.sun.management.OperatingSystemMXBean sunOs) {
                        double load = sunOs.getCpuLoad();
                        if (load >= 0) MetricsCollector.set(kLoad, (long)(load * 100));
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                catch (Exception e) { log.error("Sampler error", e); }
            }
        }, "jvm-monitor");
        t.setDaemon(true); t.start();
        log.info("JvmMonitor auto-started for app type.");
    }
}
