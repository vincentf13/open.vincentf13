package open.vincentf13.service.spot.infra.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指標靜態持有者 (極簡 Micrometer 版)
 * 職責：提供全局靜態 API，將 long key 映射為 Micrometer 指標。
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
        if (registry != null) {
            registry.counter("spot.metric." + Math.abs(key)).increment(delta);
        }
    }

    public static void setGauge(long key, long val) {
        if (registry != null) {
            GAUGE_MAP.computeIfAbsent(key, k -> registry.gauge("spot.metric." + Math.abs(k), new AtomicLong(val))).set(val);
        }
    }

    public static void recordLatency(long key, long nanos) {
        if (registry != null) {
            // 使用 Timer 並預設開啟分位數發布 (p50, p90, p99)
            Timer.builder("spot.metric." + Math.abs(key))
                 .publishPercentiles(0.5, 0.9, 0.99)
                 .register(registry)
                 .record(nanos, TimeUnit.NANOSECONDS);
        }
    }

    public static MeterRegistry getRegistry() {
        return registry;
    }
}
