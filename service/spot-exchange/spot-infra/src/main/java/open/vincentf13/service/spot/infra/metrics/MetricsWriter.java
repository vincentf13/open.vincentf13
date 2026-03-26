package open.vincentf13.service.spot.infra.metrics;

import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 指標異步寫入器 (極簡版)
 * 職責：每秒遍歷本地 JVM 的 Micrometer Registry，並將所有 spot 指標刷新到共享磁碟中。
 * 由於 Registry 是進程隔離的，此組件會自動實現「各司其職」，不會發生跨進程數據覆蓋。
 */
@Slf4j
@Component
public class MetricsWriter {

    private final MeterRegistry registry = Metrics.globalRegistry;
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-writer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        log.info("MetricsWriter initialized.");
    }

    private void tick() {
        try {
            final long now = System.currentTimeMillis();
            final Storage s = Storage.self();
            var latestMap = s.latestMetrics();
            var tpsMap = s.tpsHistory();
            var latMap = s.latencyHistory();
            AtomicInteger count = new AtomicInteger(0);

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
