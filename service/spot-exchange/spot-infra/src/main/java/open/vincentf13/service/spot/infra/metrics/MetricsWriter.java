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
 * 本類每秒批次刷入 ChronicleMap。
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
            Storage s = Storage.self();
            long now = System.currentTimeMillis();

            // 1. Gauge / Counter → latestMetrics
            StaticMetricsHolder.values().forEach((key, atom) -> s.latestMetrics().put(key, atom.get()));

            // 2. TPS 累計快照 → tpsHistory
            AtomicLong count = StaticMetricsHolder.values().get(MetricsKey.ORDER_PROCESSED_COUNT);
            if (count != null) s.tpsHistory().put(now, count.get());

            // 3. GC 事件 metadata → gcEventHistory
            List<StaticMetricsHolder.GcEvent> gcEvents = StaticMetricsHolder.drainGcEvents();
            if (!gcEvents.isEmpty()) {
                ChronicleMap<Long, String> gcMap = s.gcEventHistory();
                for (var e : gcEvents) gcMap.put(e.key(), e.meta());
            }
        } catch (Exception e) {
            log.error("Metrics tick failed", e);
        }
    }

    @PreDestroy
    public void shutdown() { scheduler.shutdown(); }
}
