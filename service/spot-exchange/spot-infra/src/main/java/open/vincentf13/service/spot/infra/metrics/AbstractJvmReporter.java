package open.vincentf13.service.spot.infra.metrics;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JVM 指標回報器 (抽象基類)
 * 職責：封裝共用的 JVM 內存監控與 GC 採樣邏輯。
 * 採用 Polling Delta 模式，確保數據統計的客觀與準確。
 */
@Slf4j
public abstract class AbstractJvmReporter {

    private final AtomicInteger gcSlot = new AtomicInteger(0);
    
    // 上次採樣的累計值
    private long lastTotalGcCount = -1;
    private long lastTotalGcTime = -1;

    @PostConstruct
    public void init() {
        // 初始化採樣基準
        updateGcStats();
        log.info("{} initialized", getClass().getSimpleName());
    }

    @Scheduled(fixedRate = 1000)
    public void reportJvm() {
        // 1. 內存指標
        Runtime r = Runtime.getRuntime();
        StaticMetricsHolder.setGauge(getUsedMbKey(), (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        StaticMetricsHolder.setGauge(getMaxMbKey(), r.maxMemory() / 1024 / 1024);

        // 2. GC 指標採樣 (Delta 模式)
        long currentTotalCount = 0;
        long currentTotalTime = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            if (count > 0) {
                currentTotalCount += count;
                currentTotalTime += time;
            }
        }

        if (lastTotalGcCount != -1) {
            long deltaCount = currentTotalCount - lastTotalGcCount;
            long deltaTimeMs = currentTotalTime - lastTotalGcTime;

            if (deltaCount > 0) {
                // 增加總計數
                StaticMetricsHolder.addCounter(getGcCountKey(), deltaCount);
                
                // 記錄歷史 (每秒一筆，聚合該秒內所有 GC 事件)
                int slot = gcSlot.getAndIncrement() % MetricsKey.GC_HISTORY_MAX_KEEP;
                // 編碼：時間戳 + 該秒內的總 STW 耗時
                long encoded = System.currentTimeMillis() * 1_000_000L 
                             + Math.min(deltaTimeMs * 1000, 999_999L);
                
                StaticMetricsHolder.setGauge(getGcHistoryStart() + slot, encoded);
                StaticMetricsHolder.setGauge(getGcDurationKey(), deltaTimeMs);
                
                log.debug("GC Detected: deltaCount={}, deltaTime={}ms", deltaCount, deltaTimeMs);
            }
        }

        lastTotalGcCount = currentTotalCount;
        lastTotalGcTime = currentTotalTime;
        
        onReport();
    }

    private void updateGcStats() {
        long count = 0, time = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gcBean.getCollectionCount();
            long t = gcBean.getCollectionTime();
            if (c > 0) {
                count += c;
                time += t;
            }
        }
        lastTotalGcCount = count;
        lastTotalGcTime = time;
    }

    protected abstract long getUsedMbKey();
    protected abstract long getMaxMbKey();
    protected abstract long getGcCountKey();
    protected abstract long getGcDurationKey();
    protected abstract long getGcHistoryStart();

    protected void onReport() {}
}
