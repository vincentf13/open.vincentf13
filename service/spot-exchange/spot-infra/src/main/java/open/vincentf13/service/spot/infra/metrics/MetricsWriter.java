package open.vincentf13.service.spot.infra.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 指標異步寫入器 (Metrics Writer - Micrometer 橋接版)
 * 職責：定期將 Micrometer 註冊表中的數據同步至 Chronicle Map。
 */
@Slf4j
@Component
public class MetricsWriter {

    private final ApplicationContext ctx;
    private final LongValue k = new LongValue();
    private final LongValue v = new LongValue();

    private long last10sWindow = 0;
    private int tickCount = 0;

    // JVM 採樣相關
    private static final Runtime RUNTIME = Runtime.getRuntime();
    private long kUsed = 0, kMax = 0;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-writer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    public MetricsWriter(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @PostConstruct
    public void start() {
        setupJvmKeys();
        scheduler.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
        log.info("MetricsWriter (Micrometer Bridge) started.");
    }

    private void setupJvmKeys() {
        try {
            var beans = ctx.getBeansWithAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class);
            if (beans.isEmpty()) return;
            String mainClassName = beans.values().iterator().next().getClass().getSimpleName();

            if (mainClassName.contains("WsApi")) {
                this.kUsed = MetricsKey.GATEWAY_JVM_USED_MB;
                this.kMax = MetricsKey.GATEWAY_JVM_MAX_MB;
            } else if (mainClassName.contains("Matching")) {
                this.kUsed = MetricsKey.MATCHING_JVM_USED_MB;
                this.kMax = MetricsKey.MATCHING_JVM_MAX_MB;
            }
        } catch (Exception e) {
            log.warn("Failed to auto-detect app type for JVM metrics", e);
        }
    }

    private void tick() {
        MeterRegistry registry = StaticMetricsHolder.getRegistry();
        if (registry == null) return;

        try {
            final long now = System.currentTimeMillis();
            final Storage s = Storage.self();
            var latestMap = s.latestMetrics();
            var tpsMap = s.tpsHistory();
            var latMap = s.latencyHistory();

            // 1. 每 1 秒執行一次 JVM 採樣
            if (++tickCount >= 10) { tickCount = 0; sampleJvm(); }

            // 2. 遍歷所有計量器並同步至 Chronicle
            registry.forEachMeter(meter -> {
                long mKey = parseKey(meter.getId().getName());
                if (mKey == 0) return;

                if (meter instanceof Counter c) {
                    long val = (long) c.count();
                    write(latestMap, mKey, val); // 儲存為負值 key (傳統習慣)
                    if (mKey == MetricsKey.MATCH_COUNT) write(tpsMap, now, val);
                } 
                else if (meter instanceof Gauge g) {
                    write(latestMap, mKey, (long) g.value());
                } 
                else if (meter instanceof Timer t && (now - last10sWindow >= 10000)) {
                    flushLatency(latMap, (now / 10000) * 10000, mKey, t);
                }
            });

            if (now - last10sWindow >= 10000) last10sWindow = (now / 10000) * 10000;

        } catch (Exception e) {
            log.error("Metrics tick failed: {}", e.getMessage());
        }
    }

    private long parseKey(String name) {
        if (!name.startsWith("spot.metric.")) return 0;
        try { return -Long.parseLong(name.substring("spot.metric.".length())); } 
        catch (Exception e) { return 0; }
    }

    private void sampleJvm() {
        if (kUsed != 0) {
            StaticMetricsHolder.setGauge(kUsed, (RUNTIME.totalMemory() - RUNTIME.freeMemory()) / 1024 / 1024);
            StaticMetricsHolder.setGauge(kMax, RUNTIME.maxMemory() / 1024 / 1024);
        }
    }

    private void flushLatency(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long window, long mKey, Timer t) {
        var snapshot = t.takeSnapshot();
        saveLatency(map, window, mKey, MetricsKey.P50, (long) snapshot.percentileValues()[0].value());
        saveLatency(map, window, mKey, MetricsKey.P90, (long) snapshot.percentileValues()[1].value());
        saveLatency(map, window, mKey, MetricsKey.P99, (long) snapshot.percentileValues()[2].value());
        saveLatency(map, window, mKey, MetricsKey.MAX, (long) snapshot.max(TimeUnit.NANOSECONDS));
    }

    private void saveLatency(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long window, long mKey, long pKey, long val) {
        long encoded = (Math.abs(mKey) * 1_000_000_000_000_000L) + (pKey * 1_000_000_000_000L) + (window / 1000);
        write(map, encoded, val);
    }

    private void write(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long key, long val) {
        k.set(key); v.set(val);
        map.put(k, v);
    }

    @PreDestroy
    public void shutdown() {
        tick();
        scheduler.shutdown();
        log.info("MetricsWriter shutdown.");
    }
}
