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
 * 指標異步寫入器 (極簡共用版)
 * 職責：每秒將本地 JVM Registry 中的 spot 指標同步至共享磁碟。
 */
@Slf4j
@Component
public class MetricsWriter {

    private final MeterRegistry registry = Metrics.globalRegistry;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-writer");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        log.info("MetricsWriter [TICKER] initialized on thread: {}", Thread.currentThread().getName());
    }

    private void tick() {
        try {
            final long now = System.currentTimeMillis();
            final Storage s = Storage.self();
            final AtomicInteger count = new AtomicInteger(0);

            registry.forEachMeter(m -> {
                String name = m.getId().getName();
                if (!name.startsWith("spot.metric.")) return;

                try {
                    long key = Long.parseLong(name.substring(12));
                    if (m instanceof Timer t) {
                        flushLatency(s.latencyHistory(), now, key, t);
                    } else {
                        long val = (long) (m instanceof Counter c ? c.count() : ((Gauge) m).value());
                        s.latestMetrics().put(new LongValue(key), new LongValue(val));
                    }
                    count.incrementAndGet();
                } catch (Exception ignored) {}
            });
            
            if (count.get() > 0) {
                log.debug("Metrics ticker flushed {} metrics to disk.", count.get());
            }
        } catch (Exception e) {
            log.error("Metrics tick failed", e);
        }
    }

    private void flushLatency(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long now, long key, Timer t) {
        var snapshot = t.takeSnapshot();
        save(map, now, key, MetricsKey.P50, (long) (snapshot.percentileValues().length > 0 ? snapshot.percentileValues()[0].value() : 0));
        save(map, now, key, MetricsKey.P99, (long) (snapshot.percentileValues().length > 1 ? snapshot.percentileValues()[1].value() : 0));
        save(map, now, key, MetricsKey.MAX, (long) snapshot.max(TimeUnit.NANOSECONDS));
    }

    private void save(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long now, long key, long pKey, long val) {
        long encoded = (key * 1_000_000_000_000_000L) + (pKey * 1_000_000_000_000L) + (now / 1000);
        map.put(new LongValue(encoded), new LongValue(val));
    }

    @PreDestroy
    public void shutdown() { scheduler.shutdown(); }
}
