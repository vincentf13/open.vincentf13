package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 指標異步寫入器 (全局 Micrometer 版)
 */
@Slf4j
@Component
public class MetricsWriter {

    private final MeterRegistry registry = Metrics.globalRegistry;
    private final org.springframework.context.ApplicationContext ctx;
    
    private long kUsed = 0, kMax = 0, kGcCount = 0, kGcDuration = 0;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-writer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    public MetricsWriter(org.springframework.context.ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @PostConstruct
    public void start() {
        setupJvmKeys();
        startGcListening();
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        log.info("MetricsWriter started using Global Registry.");
    }

    private void setupJvmKeys() {
        try {
            var beans = ctx.getBeansWithAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class);
            if (!beans.isEmpty()) {
                String mainName = beans.values().iterator().next().getClass().getSimpleName();
                if (mainName.contains("WsApi")) { 
                    kUsed = MetricsKey.GATEWAY_JVM_USED_MB; kMax = MetricsKey.GATEWAY_JVM_MAX_MB; 
                    kGcCount = MetricsKey.GATEWAY_GC_COUNT; kGcDuration = MetricsKey.GATEWAY_GC_LAST_DURATION_MS;
                }
                else if (mainName.contains("Matching")) { 
                    kUsed = MetricsKey.MATCHING_JVM_USED_MB; kMax = MetricsKey.MATCHING_JVM_MAX_MB; 
                    kGcCount = MetricsKey.MATCHING_GC_COUNT; kGcDuration = MetricsKey.MATCHING_GC_LAST_DURATION_MS;
                }
            }
        } catch (Exception ignored) {}
    }

    private void startGcListening() {
        if (kGcCount == 0) return;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
                        if (!info.getGcAction().toLowerCase().contains("end of")) return;
                        StaticMetricsHolder.setGauge(kGcDuration, info.getGcInfo().getDuration());
                        StaticMetricsHolder.addCounter(kGcCount, 1);
                    }
                }, null, null);
            }
        }
    }

    private void tick() {
        try {
            final long now = System.currentTimeMillis();
            final Storage s = Storage.self();
            var latestMap = s.latestMetrics();
            var tpsMap = s.tpsHistory();
            var latMap = s.latencyHistory();
            AtomicInteger count = new AtomicInteger(0);

            // 1. 同步 JVM 指標
            if (kUsed != 0) {
                Runtime r = Runtime.getRuntime();
                write(latestMap, kUsed, (r.totalMemory() - r.freeMemory()) / 1024 / 1024); 
                write(latestMap, kMax, r.maxMemory() / 1024 / 1024);
                count.addAndGet(2);
            }

            // 2. 遍歷全局註冊表並同步
            registry.forEachMeter(meter -> {
                String name = meter.getId().getName();
                if (!name.startsWith("spot.metric.")) return;

                try {
                    long mKey = Long.parseLong(name.substring(12));
                    if (meter instanceof Counter c) {
                        long val = (long) c.count();
                        write(latestMap, mKey, val);
                        if (mKey == MetricsKey.MATCH_COUNT) write(tpsMap, now, val);
                        count.incrementAndGet();
                    } 
                    else if (meter instanceof Gauge g) {
                        write(latestMap, mKey, (long) g.value());
                        count.incrementAndGet();
                    }
                    else if (meter instanceof Timer t) {
                        flushLatency(latMap, now, mKey, t);
                        count.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            log.error("Metrics tick failed: {}", e.getMessage());
        }
    }

    private void flushLatency(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long now, long mKey, Timer t) {
        var snapshot = t.takeSnapshot();
        saveLatency(map, now, mKey, MetricsKey.P50, (long) (snapshot.percentileValues().length > 0 ? snapshot.percentileValues()[0].value() : 0));
        saveLatency(map, now, mKey, MetricsKey.P99, (long) (snapshot.percentileValues().length > 1 ? snapshot.percentileValues()[1].value() : 0));
        saveLatency(map, now, mKey, MetricsKey.MAX, (long) snapshot.max(TimeUnit.NANOSECONDS));
    }

    private void saveLatency(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long now, long mKey, long pKey, long val) {
        long encoded = (Math.abs(mKey) * 1_000_000_000_000_000L) + (pKey * 1_000_000_000_000L) + (now / 1000);
        map.put(new LongValue(encoded), new LongValue(val));
    }

    private void write(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long key, long val) {
        map.put(new LongValue(key), new LongValue(val));
    }

    @PreDestroy
    public void shutdown() { scheduler.shutdown(); }
}
