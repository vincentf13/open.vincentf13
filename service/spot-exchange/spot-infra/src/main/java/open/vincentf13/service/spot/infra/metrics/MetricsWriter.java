package open.vincentf13.service.spot.infra.metrics;

import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 指標同步寫入器
 * 每秒從 StaticMetricsHolder 讀取全部指標並持久化至 ChronicleMap。
 */
@Slf4j
@Component
public class MetricsWriter {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "metrics-writer");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        log.info("MetricsWriter started");
    }

    private void tick() {
        try {
            final Storage s = Storage.self();
            final long now = System.currentTimeMillis();

            // 將所有 Gauge / Counter 快照寫入共享 Chronicle Map
            StaticMetricsHolder.values().forEach((key, atom) ->
                s.latestMetrics().put(key, atom.get())
            );

            // 將延遲 Timer 的百分位數寫入 latencyHistory
            StaticMetricsHolder.timerRegistry().forEachMeter(m -> {
                if (!(m instanceof Timer t)) return;
                String name = m.getId().getName();
                if (!name.startsWith("spot.latency.")) return;
                try {
                    long key = Long.parseLong(name.substring("spot.latency.".length()));
                    flushLatency(s.latencyHistory(), now, key, t);
                } catch (Exception e) {
                    log.warn("Failed to flush latency [{}]: {}", name, e.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("Metrics tick failed", e);
        }
    }

    private void flushLatency(ChronicleMap<Long, Long> map, long now, long key, Timer t) {
        var snapshot = t.takeSnapshot();
        if (snapshot.count() == 0) return;
        var pv = snapshot.percentileValues();
        save(map, now, key, MetricsKey.P50, pv.length > 0 ? (long) pv[0].value() : 0);
        save(map, now, key, MetricsKey.P99, pv.length > 1 ? (long) pv[1].value() : 0);
        save(map, now, key, MetricsKey.MAX, (long) snapshot.max(TimeUnit.NANOSECONDS));
    }

    // 編碼公式：Key(10^15) + Percentile(10^12) + EpochSecond(10^0)
    private void save(ChronicleMap<Long, Long> map, long now, long key, long pKey, long val) {
        map.put((key * 1_000_000_000_000_000L) + (pKey * 1_000_000_000_000L) + (now / 1000), val);
    }

    @PreDestroy
    public void shutdown() { scheduler.shutdown(); }
}
