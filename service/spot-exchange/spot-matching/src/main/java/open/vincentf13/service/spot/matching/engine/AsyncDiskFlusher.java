package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 非同步磁碟落盤排程器 (Async Disk Flusher)
 *
 * 專用背景 thread 負責將 matching 產生的 dirty state 寫入 ChronicleMap，
 * 將原本阻塞 matching receiver ~100ms/sec 的批量 put 操作移出關鍵路徑。
 *
 * 不需要效率優化也不綁核：
 * - 單 thread daemon scheduled executor
 * - 由 OS 排程，使用 matching JVM 分配到的任一核心
 * - 吞吐量 target: ~250K puts/sec（足以應付 60K/sec 訂單）
 */
@Slf4j
@Component
public class AsyncDiskFlusher {

    private static final long FLUSH_INTERVAL_MS = 20;

    private final List<DiskSink> sinks = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "matching-disk-flusher");
        t.setDaemon(true);
        return t;
    });

    public void register(DiskSink sink) {
        sinks.add(sink);
        log.info("AsyncDiskFlusher 註冊 sink: {}", sink.getClass().getSimpleName());
    }

    @PostConstruct
    public void start() {
        executor.scheduleWithFixedDelay(this::tick, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.info("AsyncDiskFlusher 啟動，interval={}ms", FLUSH_INTERVAL_MS);
    }

    private void tick() {
        for (DiskSink s : sinks) {
            try {
                s.drainToDisk();
            } catch (Exception e) {
                log.error("flusher tick failed for {}: {}", s.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 最終排空：確保關閉時所有 dirty state 落盤
        for (DiskSink s : sinks) {
            try { s.drainToDisk(); } catch (Exception e) { log.error("final drain failed", e); }
        }
        log.info("AsyncDiskFlusher 已停止");
    }
}
