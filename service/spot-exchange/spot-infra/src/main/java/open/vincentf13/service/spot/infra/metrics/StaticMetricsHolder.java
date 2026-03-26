package open.vincentf13.service.spot.infra.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指標靜態門面
 * 直接持有 AtomicLong Map，消除 Spring 初始化時序問題。
 * 延遲指標另用獨立的 SimpleMeterRegistry 保留 HDR 百分位計算。
 */
public class StaticMetricsHolder {

    private static final ConcurrentHashMap<Long, AtomicLong> VALUES = new ConcurrentHashMap<>();
    private static final SimpleMeterRegistry TIMER_REGISTRY = new SimpleMeterRegistry();

    public static void addCounter(long key, long delta) {
        VALUES.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(delta);
    }

    public static void setGauge(long key, long val) {
        VALUES.computeIfAbsent(key, k -> new AtomicLong()).set(val);
    }

    public static void recordCpuId(long key, int cpuId) {
        if (cpuId < 0 || cpuId >= 64) return;
        VALUES.computeIfAbsent(key, k -> new AtomicLong()).accumulateAndGet(1L << cpuId, (prev, x) -> prev | x);
    }

    public static void recordLatency(long key, long nanos) {
        Timer.builder("spot.latency." + key)
             .publishPercentiles(0.5, 0.99)
             .register(TIMER_REGISTRY)
             .record(nanos, TimeUnit.NANOSECONDS);
    }

    public static Map<Long, AtomicLong> values() { return VALUES; }

    public static MeterRegistry timerRegistry() { return TIMER_REGISTRY; }
}
