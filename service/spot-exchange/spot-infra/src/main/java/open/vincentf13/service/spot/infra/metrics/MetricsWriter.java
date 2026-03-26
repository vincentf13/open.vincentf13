package open.vincentf13.service.spot.infra.metrics;

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
 * 指標異步寫入器 (Metrics Writer)
 * 職責：定期將 Counter/Gauge/Latency 指標持久化到 Chronicle Storage。
 */
@Slf4j
@Component
public class MetricsWriter {

    private final ApplicationContext ctx;
    private final CounterMetrics counters = CounterMetrics.INSTANCE;
    private final GaugeMetrics gauges = GaugeMetrics.INSTANCE;
    private final LatencyMetrics latencies = LatencyMetrics.INSTANCE;

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
        log.info("MetricsWriter started.");
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
        try {
            final long now = System.currentTimeMillis();
            final Storage s = Storage.self();

            // 1. 每 1 秒 (10 ticks) 執行一次採樣
            if (++tickCount >= 10) {
                tickCount = 0;
                sampleJvm();
            }

            // 2. 持久化計數器與 TPS (每 100ms)
            var latestMap = s.latestMetrics();
            var tpsMap = s.tpsHistory();
            counters.forEach((idx, val) -> {
                write(latestMap, -idx.longValue(), val);
                if (idx == (int) Math.abs(MetricsKey.MATCH_COUNT)) {
                    write(tpsMap, now, val);
                }
            });

            // 3. 持久化絕對值快照 (每 100ms)
            gauges.forEachDirty((idx, val) -> write(latestMap, -idx.longValue(), val));

            // 4. 每 10 秒持久化延遲分佈
            if (now - last10sWindow >= 10000) {
                last10sWindow = (now / 10000) * 10000;
                final long window = last10sWindow;
                var latMap = s.latencyHistory();
                
                latencies.consume(MetricsKey.LATENCY_MATCHING, (key, h) -> flushLatency(latMap, window, key, h));
                latencies.consume(MetricsKey.LATENCY_TRANSPORT, (key, h) -> flushLatency(latMap, window, key, h));
            }

        } catch (Exception e) {
            log.error("Metrics tick failed: {}", e.getMessage());
        }
    }

    private void sampleJvm() {
        if (kUsed != 0) {
            GaugeMetrics.set(kUsed, (RUNTIME.totalMemory() - RUNTIME.freeMemory()) / 1024 / 1024);
            GaugeMetrics.set(kMax, RUNTIME.maxMemory() / 1024 / 1024);
        }
    }

    private void flushLatency(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long window, long mKey, org.HdrHistogram.Histogram h) {
        saveLatency(map, window, mKey, MetricsKey.P50, h.getValueAtPercentile(50.0));
        saveLatency(map, window, mKey, MetricsKey.P90, h.getValueAtPercentile(90.0));
        saveLatency(map, window, mKey, MetricsKey.P99, h.getValueAtPercentile(99.0));
        saveLatency(map, window, mKey, MetricsKey.P999, h.getValueAtPercentile(99.9));
        saveLatency(map, window, mKey, MetricsKey.MAX, h.getMaxValue());
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
