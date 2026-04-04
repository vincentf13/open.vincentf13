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
 * 所有 ChronicleMap 指標寫入的唯一入口。
 * 其他線程僅寫入 StaticMetricsHolder (in-memory)，
 * 本類每秒批次刷入 ChronicleMap，消除跨線程磁碟 I/O 競爭。
 */
@Slf4j
@Component
public class MetricsWriter {

    // 延遲歷史 key 編碼：metricKey * KEY_UNIT + percentile * PERCENTILE_UNIT + epochSecond
    private static final long KEY_UNIT = 1_000_000_000_000_000L;
    private static final long PERCENTILE_UNIT = 1_000_000_000_000L;

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
            Storage s = Storage.self();
            long now = System.currentTimeMillis();

            flushGauges(s);
            flushLatencies(s, now);
            flushTps(s, now);
            flushGcEvents(s);
        } catch (Exception e) {
            log.error("Metrics tick failed", e);
        }
    }

    private void flushGauges(Storage s) {
        StaticMetricsHolder.values().forEach((key, atom) -> s.latestMetrics().put(key, atom.get()));
    }

    private void flushLatencies(Storage s, long now) {
        long epochSec = now / 1000;
        ChronicleMap<Long, Long> map = s.latencyHistory();
        StaticMetricsHolder.snapshotLatencyAndReset().forEach((key, snap) -> {
            long base = key * KEY_UNIT;
            map.put(base + MetricsKey.P50 * PERCENTILE_UNIT + epochSec, snap.p50());
            map.put(base + MetricsKey.P99 * PERCENTILE_UNIT + epochSec, snap.p99());
            map.put(base + MetricsKey.MAX * PERCENTILE_UNIT + epochSec, snap.max());
        });
    }

    private void flushTps(Storage s, long now) {
        AtomicLong count = StaticMetricsHolder.values().get(MetricsKey.ORDER_PROCESSED_COUNT);
        if (count != null) s.tpsHistory().put(now, count.get());
    }

    private void flushGcEvents(Storage s) {
        List<StaticMetricsHolder.GcEvent> events = StaticMetricsHolder.drainGcEvents();
        if (!events.isEmpty()) {
            ChronicleMap<Long, String> gcMap = s.gcEventHistory();
            for (var e : events) gcMap.put(e.key(), e.meta());
        }
    }

    @PreDestroy
    public void shutdown() { scheduler.shutdown(); }
}
