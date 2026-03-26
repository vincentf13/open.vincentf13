package open.vincentf13.service.spot.infra.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指標靜態持有者 (增強位圖與歷史支持)
 */
@Slf4j
@Component
public class StaticMetricsHolder {

    private static MeterRegistry registry;
    private static final Map<Long, AtomicLong> GAUGE_MAP = new ConcurrentHashMap<>();

    public StaticMetricsHolder(MeterRegistry registry) {
        StaticMetricsHolder.registry = registry;
        log.info("StaticMetricsHolder initialized with Micrometer registry.");
    }

    public static void addCounter(long key, long delta) {
        if (registry != null) registry.counter("spot.metric." + Math.abs(key)).increment(delta);
    }

    public static void setGauge(long key, long val) {
        if (registry != null) {
            GAUGE_MAP.computeIfAbsent(key, k -> registry.gauge("spot.metric." + Math.abs(k), new AtomicLong(val))).set(val);
        }
    }

    /** 記錄 CPU 位圖：將目前的 cpuId 疊加到 bitmask 中 */
    public static void recordCpuId(long key, int cpuId) {
        if (cpuId < 0 || cpuId >= 64) return;
        long bit = 1L << cpuId;
        GAUGE_MAP.computeIfAbsent(key, k -> {
            AtomicLong al = new AtomicLong(0);
            if (registry != null) registry.gauge("spot.metric." + Math.abs(k), al);
            return al;
        }).accumulateAndGet(bit, (prev, x) -> prev | x);
    }

    public static void recordLatency(long key, long nanos) {
        if (registry != null) {
            Timer.builder("spot.metric." + Math.abs(key))
                 .publishPercentiles(0.5, 0.9, 0.99)
                 .register(registry)
                 .record(nanos, TimeUnit.NANOSECONDS);
        }
    }

    public static MeterRegistry getRegistry() { return registry; }
}
