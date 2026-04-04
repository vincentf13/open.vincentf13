package open.vincentf13.service.spot.infra.metrics;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指標單一寫入器 (Metrics Single Writer)
 *
 * 所有 ChronicleMap 指標寫入的唯一入口：
 * - latestMetrics：全部 gauge/counter 快照
 * - latencyHistory：延遲百分位數
 * - tpsHistory：TPS 累計快照
 * - gcEventHistory：GC 事件 metadata
 *
 * 其他線程（hot-path、GC notification、@Scheduled）僅寫入 StaticMetricsHolder (in-memory)，
 * 本類每秒批次刷入 ChronicleMap，消除跨線程磁碟 I/O 競爭。
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

            // 1. Gauge / Counter → latestMetrics
            StaticMetricsHolder.values().forEach((key, atom) ->
                s.latestMetrics().put(key, atom.get()));

            // 2. 延遲百分位數 → latencyHistory
            StaticMetricsHolder.snapshotLatencyAndReset().forEach((key, snapshot) ->
                flushLatency(s.latencyHistory(), now, key, snapshot));

            // 3. TPS 累計快照 → tpsHistory (僅當 ORDER_PROCESSED_COUNT 存在時)
            AtomicLong orderCount = StaticMetricsHolder.values().get(MetricsKey.ORDER_PROCESSED_COUNT);
            if (orderCount != null) {
                s.tpsHistory().put(now, orderCount.get());
            }

            // 4. GC 事件 metadata → gcEventHistory (批次排空緩衝)
            List<StaticMetricsHolder.GcEvent> gcEvents = StaticMetricsHolder.drainGcEvents();
            if (!gcEvents.isEmpty()) {
                ChronicleMap<Long, String> gcMap = s.gcEventHistory();
                for (var event : gcEvents) {
                    gcMap.put(event.key(), event.meta());
                }
            }

        } catch (Exception e) {
            log.error("Metrics tick failed", e);
        }
    }

    private void flushLatency(ChronicleMap<Long, Long> map, long now, long key, StaticMetricsHolder.LatencySnapshot snapshot) {
        long epochSec = now / 1000;
        long base = key * 1_000_000_000_000_000L;
        map.put(base + MetricsKey.P50 * 1_000_000_000_000L + epochSec, snapshot.p50());
        map.put(base + MetricsKey.P99 * 1_000_000_000_000L + epochSec, snapshot.p99());
        map.put(base + MetricsKey.MAX * 1_000_000_000_000L + epochSec, snapshot.max());
    }

    @PreDestroy
    public void shutdown() { scheduler.shutdown(); }
}
