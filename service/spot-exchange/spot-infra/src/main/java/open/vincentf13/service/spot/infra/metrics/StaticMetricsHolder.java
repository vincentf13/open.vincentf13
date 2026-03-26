package open.vincentf13.service.spot.infra.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指標靜態持有者 (Static Metrics Holder)
 * 職責：提供全局靜態 API，內部代理至 Micrometer MeterRegistry。
 * 優點：簡化埋點代碼，同時保留 Micrometer 的專業統計能力。
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

    /** 獲取底層註冊表 */
    public static MeterRegistry getRegistry() { return registry; }

    /** 累加計數器 */
    public static void addCounter(long key, long delta) {
        if (registry == null) return;
        registry.counter("spot.metric." + Math.abs(key)).increment(delta);
    }

    /** 設置絕對值 */
    public static void setGauge(long key, long val) {
        if (registry == null) return;
        GAUGE_MAP.computeIfAbsent(key, k -> registry.gauge("spot.metric." + Math.abs(k), new AtomicLong(val))).set(val);
    }

    /** 記錄延遲 */
    public static void recordLatency(long key, long nanos) {
        if (registry == null) return;
        // 使用 Timer 並預設開啟分位數發布 (p50, p90, p99)
        Timer.builder("spot.metric." + Math.abs(key))
             .publishPercentiles(0.5, 0.9, 0.99)
             .register(registry)
             .record(nanos, TimeUnit.NANOSECONDS);
    }
}
