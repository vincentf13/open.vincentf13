package open.vincentf13.service.spot.infra.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 統一指標註冊表 (Micrometer 版)
 * 職責：封裝 Micrometer API，並提供適配 Chronicle Map 的遍歷接口。
 */
@Component
public class SpotMetrics {

    @Getter
    private final MeterRegistry registry;
    private final Map<Long, String> keyToName = new HashMap<>();

    public SpotMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // --- Counter ---
    public void addCounter(long key, long delta) {
        String name = getName(key);
        registry.counter(name).increment(delta);
    }

    // --- Gauge ---
    public void setGauge(long key, long val) {
        String name = getName(key);
        // Micrometer 的 Gauge 是觀察式的，這裡我們用 AtomicLong 橋接
        registry.gauge(name, new java.util.concurrent.atomic.AtomicLong(val)).set(val);
    }

    // --- Timer (Latency) ---
    public void recordLatency(long key, long nanos) {
        String name = getName(key);
        registry.timer(name).record(nanos, TimeUnit.NANOSECONDS);
    }

    private String getName(long key) {
        return keyToName.computeIfAbsent(key, k -> "spot.metric." + Math.abs(k));
    }

    /** 供 MetricsWriter 使用：遍歷所有 Counter */
    public void forEachCounter(BiConsumer<Long, Long> action) {
        registry.forEachMeter(meter -> {
            if (meter instanceof Counter c) {
                action.accept(parseKey(c.getId().getName()), (long) c.count());
            }
        });
    }

    /** 供 MetricsWriter 使用：遍歷所有 Gauge */
    public void forEachGauge(BiConsumer<Long, Long> action) {
        registry.forEachMeter(meter -> {
            if (meter instanceof Gauge g) {
                action.accept(parseKey(g.getId().getName()), (long) g.value());
            }
        });
    }

    /** 供 MetricsWriter 使用：遍歷所有 Timer 並獲取百分位數 */
    public void forEachTimer(BiConsumer<Long, Map<String, Long>> action) {
        registry.forEachMeter(meter -> {
            if (meter instanceof Timer t) {
                Map<String, Long> stats = new HashMap<>();
                var snapshot = t.takeSnapshot();
                stats.put("p50", (long) snapshot.percentileValues()[0].value()); // 需配置分位數
                stats.put("p99", (long) snapshot.percentileValues()[1].value());
                stats.put("max", (long) snapshot.max(TimeUnit.NANOSECONDS));
                action.accept(parseKey(t.getId().getName()), stats);
            }
        });
    }

    private long parseKey(String name) {
        try {
            return -Long.parseLong(name.substring("spot.metric.".length()));
        } catch (Exception e) {
            return 0;
        }
    }
}
