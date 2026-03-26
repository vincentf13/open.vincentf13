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

/**
 * 指標異步寫入器 (Micrometer 橋接版)
 * 職責：定期將 Micrometer 註冊表中的數據同步至 Chronicle Map (latest, tps, latency)。
 */
@Slf4j
@Component
public class MetricsWriter {

    private final MeterRegistry registry;
    private final LongValue k = new LongValue();
    private final LongValue v = new LongValue();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-writer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    public MetricsWriter(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    private void tick() {
        try {
            final long now = System.currentTimeMillis();
            final Storage s = Storage.self();
            var latestMap = s.latestMetrics();
            var tpsMap = s.tpsHistory();
            var latMap = s.latencyHistory();

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
                    else if (meter instanceof Gauge g) {
                        write(latestMap, mKey, (long) g.value());
                    } 
                    else if (meter instanceof Timer t) {
                        write(latestMap, mKey, (long) t.mean(TimeUnit.NANOSECONDS));
                        flushLatency(latMap, now, mKey, t);
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            log.error("Metrics tick failed: {}", e.getMessage());
        }
    }

    private void flushLatency(net.openhft.chronicle.map.ChronicleMap<LongValue, LongValue> map, long now, long mKey, Timer t) {
        var snapshot = t.takeSnapshot();
        // 延遲統計的編碼 Key：(Key絕對值 * 10^15) + (分位數Key * 10^12) + (秒級時間戳)
        saveLatency(map, now, mKey, MetricsKey.P50, (long) (snapshot.percentileValues().length > 0 ? snapshot.percentileValues()[0].value() : 0));
        saveLatency(map, now, mKey, MetricsKey.P90, (long) (snapshot.percentileValues().length > 1 ? snapshot.percentileValues()[1].value() : 0));
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
    public void shutdown() {
        scheduler.shutdown();
    }
}
