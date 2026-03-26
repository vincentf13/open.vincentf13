package open.vincentf13.service.spot.infra.metrics;

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

            // 將秒級延遲窗口的百分位數寫入 latencyHistory
            StaticMetricsHolder.snapshotLatencyAndReset().forEach((key, snapshot) ->
                flushLatency(s.latencyHistory(), now, key, snapshot)
            );

        } catch (Exception e) {
            log.error("Metrics tick failed", e);
        }
    }

    private void flushLatency(ChronicleMap<Long, Long> map, long now, long key, StaticMetricsHolder.LatencySnapshot snapshot) {
        save(map, now, key, MetricsKey.P50, snapshot.p50());
        save(map, now, key, MetricsKey.P99, snapshot.p99());
        save(map, now, key, MetricsKey.MAX, snapshot.max());
    }

    // 編碼公式：Key(10^15) + Percentile(10^12) + EpochSecond(10^0)
    private void save(ChronicleMap<Long, Long> map, long now, long key, long pKey, long val) {
        map.put((key * 1_000_000_000_000_000L) + (pKey * 1_000_000_000_000L) + (now / 1000), val);
    }

    @PreDestroy
    public void shutdown() { scheduler.shutdown(); }
}
