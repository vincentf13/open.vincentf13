package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 指標異步寫入器 (極簡加強版)
 */
@Slf4j
@Component
public class MetricsWriter {

    private final MeterRegistry registry;
    private final org.springframework.context.ApplicationContext ctx;
    private final LongValue k = new LongValue();
    private final LongValue v = new LongValue();
    private long kUsed = 0, kMax = 0, kGcCount = 0, kGcDuration = 0;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-writer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    public MetricsWriter(ObjectProvider<MeterRegistry> registryProvider, org.springframework.context.ApplicationContext ctx) {
        this.registry = registryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.ctx = ctx;
    }

    @PostConstruct
    public void start() {
        setupJvmKeys();
        startGcListening();
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
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

    /** 監聽 GC 事件：直接獲取「每次」執行時間指標 */
    private void startGcListening() {
        if (kGcCount == 0) return;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
                        if (!info.getGcAction().toLowerCase().contains("end of")) return;

                        long duration = info.getGcInfo().getDuration();
                        // 1. 同步最近一次 GC 指標
                        StaticMetricsHolder.setGauge(kGcDuration, duration);
                        StaticMetricsHolder.addCounter(kGcCount, 1);
                        log.info("JVM GC detected: {} ms", duration);
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

            // 同步 JVM 內存 (使用 Spring 內部統計)
            if (kUsed != 0) {
                try {
                    long used = (long) registry.get("jvm.memory.used").tag("area", "heap").gauge().value() / 1024 / 1024;
                    long max = (long) registry.get("jvm.memory.max").tag("area", "heap").gauge().value() / 1024 / 1024;
                    write(latestMap, kUsed, used); write(latestMap, kMax, max);
                } catch (Exception ignored) {}
            }

            registry.forEachMeter(meter -> {
                String name = meter.getId().getName();
                if (!name.startsWith("spot.metric.")) return;

                try {
                    long mKey = -Long.parseLong(name.substring(12));
                    if (meter instanceof Counter c) {
                        long val = (long) c.count();
                        write(latestMap, mKey, val);
                        if (mKey == MetricsKey.MATCH_COUNT) write(tpsMap, now, val);
                    } 
                    else if (meter instanceof Gauge g) write(latestMap, mKey, (long) g.value());
                    else if (meter instanceof Timer t) flushLatency(latMap, now, mKey, t);
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            log.error("Metrics tick failed: {}", e.getMessage());
        }
    }

    private void flushLatency(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long now, long mKey, Timer t) {
        var snapshot = t.takeSnapshot();
        saveLatency(map, now, mKey, MetricsKey.P50, (long) (snapshot.percentileValues().length > 0 ? snapshot.percentileValues()[0].value() : 0));
        saveLatency(map, now, mKey, MetricsKey.P99, (long) (snapshot.percentileValues().length > 2 ? snapshot.percentileValues()[2].value() : 0));
        saveLatency(map, now, mKey, MetricsKey.MAX, (long) snapshot.max(TimeUnit.NANOSECONDS));
    }

    private void saveLatency(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long now, long mKey, long pKey, long val) {
        long encoded = (Math.abs(mKey) * 1_000_000_000_000_000L) + (pKey * 1_000_000_000_000L) + (now / 1000);
        write(map, encoded, val);
    }

    private void write(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long key, long val) {
        k.set(key); v.set(val);
        map.put(k, v);
    }

    @PreDestroy
    public void shutdown() { scheduler.shutdown(); }
}
