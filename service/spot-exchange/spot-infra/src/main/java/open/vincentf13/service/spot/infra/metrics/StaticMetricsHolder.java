package open.vincentf13.service.spot.infra.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指標靜態持有者 (全局 Micrometer 版)
 * 職責：使用 Metrics.globalRegistry，確保指標始終能被捕捉，不受 Spring Bean 初始化順序影響。
 */
@Slf4j
@Component
public class StaticMetricsHolder {

    private static final MeterRegistry REGISTRY = Metrics.globalRegistry;
    private static final Map<Long, AtomicLong> GAUGE_MAP = new ConcurrentHashMap<>();

    public StaticMetricsHolder() {
        log.info("StaticMetricsHolder initialized using Global Registry.");
    }

    public static void addCounter(long key, long delta) {
        REGISTRY.counter("spot.metric." + key).increment(delta);
    }

    public static void setGauge(long key, long val) {
        GAUGE_MAP.computeIfAbsent(key, k -> {
            AtomicLong al = new AtomicLong(val);
            REGISTRY.gauge("spot.metric." + k, al);
            return al;
        }).set(val);
    }

    /** 記錄 CPU 位圖：將目前的 cpuId 疊加到 bitmask 中 */
    public static void recordCpuId(long key, int cpuId) {
        long bit = 1L << cpuId;
        GAUGE_MAP.computeIfAbsent(key, k -> {
            AtomicLong al = new AtomicLong(0);
            REGISTRY.gauge("spot.metric." + k, al);
            return al;
        }).accumulateAndGet(bit, (prev, x) -> prev | x);
    }

    public static void recordLatency(long key, long nanos) {
        Timer.builder("spot.metric." + key)
             .publishPercentiles(0.5, 0.9, 0.99, 0.999)
             .register(REGISTRY)
             .record(nanos, TimeUnit.NANOSECONDS);
    }

    public static MeterRegistry getRegistry() { return REGISTRY; }
}
