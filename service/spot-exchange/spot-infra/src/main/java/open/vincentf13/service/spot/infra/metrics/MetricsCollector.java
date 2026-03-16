package open.vincentf13.service.spot.infra.metrics;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.agrona.concurrent.QueuedSingleThreadExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 統一監控埋點工具類 (Metrics Collector)
 * 採用異步模式，確保統計行為不影響主交易線程效能
 */
@Slf4j
public class MetricsCollector {

    // 使用單線程異步執行器，保證指標寫入 Chronicle Map 的順序性且不阻塞主線程
    private static final Executor METRICS_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "metrics-collector");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /**
     * 異步更新計數器 (如 netty_recv_count, engine_work_count)
     */
    public static void increment(long key) {
        METRICS_EXECUTOR.execute(() -> {
            Storage.self().metricsHistory().compute(key, (k, v) -> v == null ? 1L : v + 1);
        });
    }

    /**
     * 異步增加特定數值 (如 backpressure_count)
     */
    public static void add(long key, long delta) {
        if (delta <= 0) return;
        METRICS_EXECUTOR.execute(() -> {
            Storage.self().metricsHistory().compute(key, (k, v) -> v == null ? delta : v + delta);
        });
    }

    /**
     * 異步設置絕對數值 (如 jvm_memory_used)
     */
    public static void set(long key, long value) {
        METRICS_EXECUTOR.execute(() -> {
            Storage.self().metricsHistory().put(key, value);
        });
    }

    /**
     * 紀錄 CPU 綁核狀態
     */
    public static void recordCpuAffinity(long key, int cpuId) {
        set(key, (long) cpuId);
    }
}
