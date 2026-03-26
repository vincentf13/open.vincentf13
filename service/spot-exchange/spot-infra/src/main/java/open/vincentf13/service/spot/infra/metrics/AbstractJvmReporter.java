package open.vincentf13.service.spot.infra.metrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import org.springframework.scheduling.annotation.Scheduled;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JVM 指標回報器 (抽象基類)
 * 職責：封裝共用的 JVM 內存監控與 GC 監聽邏輯。
 */
@Slf4j
public abstract class AbstractJvmReporter {

    // 循環寫入 GC 歷史槽位的計數器
    private final AtomicInteger gcSlot = new AtomicInteger(0);
    // 累加器：暫存每個 GC ID 的總耗時 (ms)
    private final Map<Long, Long> gcIdDurationMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean instanceof NotificationEmitter emitter) {
                emitter.addNotificationListener((notification, handback) -> {
                    if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) return;
                    CompositeData cd = (CompositeData) notification.getUserData();
                    GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
                    
                    long gcId = info.getGcInfo().getId();
                    long durationMs = info.getGcInfo().getDuration();
                    
                    // 累加當前階段的耗時
                    gcIdDurationMap.merge(gcId, durationMs, Long::sum);

                    // 檢查是否為該次 GC 的最後一個階段
                    // 註：通常 GC 通知本身就是階段結束，但我們可以透過 gcAction 判定
                    if (info.getGcAction().toLowerCase().contains("end of")) {
                        // 為了確保捕獲所有階段，我們可以延遲一小段時間或根據 GC 類型判定
                        // 對於高頻交易系統，我們通常直接在上報時取出累計值
                        long totalDurationMs = gcIdDurationMap.get(gcId);
                        
                        // 如果是最終階段 (例如 G1 的最後一個 pause)，執行上報
                        // 這裡我們優化為：只要有耗時，就更新該 ID 的最終狀態
                        if (totalDurationMs > 0) {
                            reportGc(gcId, totalDurationMs);
                        }
                    }
                }, null, null);
            }
        }
        log.info("{} initialized", getClass().getSimpleName());
    }

    private final Map<Long, Boolean> reportedGcIds = new ConcurrentHashMap<>();

    private void reportGc(long gcId, long totalDurationMs) {
        // 確保每個 GC ID 在歷史紀錄中只佔一個坑位 (更新制)
        // 我們使用一個小的 Cache 來防止同一個 ID 被重複計入 count
        if (reportedGcIds.putIfAbsent(gcId, true) == null) {
            StaticMetricsHolder.addCounter(getGcCountKey(), 1);
            int slot = gcSlot.getAndIncrement() % MetricsKey.GC_HISTORY_MAX_KEEP;
            // 初始寫入
            updateGcSlot(slot, gcId, totalDurationMs);
        } else {
            // 如果 ID 已存在，更新同一個 slot (找到剛才那個 slot)
            // 為了簡化，在高並發下我們接受微小的 count 誤差，或使用更複雜的 ID -> Slot 映射
            // 這裡採用簡單策略：如果總耗時增加，代表有新階段加入，我們更新最新的紀錄
            int lastSlot = (gcSlot.get() - 1 + MetricsKey.GC_HISTORY_MAX_KEEP) % MetricsKey.GC_HISTORY_MAX_KEEP;
            updateGcSlot(lastSlot, gcId, totalDurationMs);
        }
    }

    private void updateGcSlot(int slot, long gcId, long totalDurationMs) {
        long encoded = System.currentTimeMillis() * 1_000_000L
                     + Math.min(totalDurationMs * 1000, 999_999L);
        StaticMetricsHolder.setGauge(getGcHistoryStart() + slot, encoded);
        StaticMetricsHolder.setGauge(getGcDurationKey(), totalDurationMs);
    }

    @Scheduled(fixedRate = 1000)
    public void reportJvm() {
        Runtime r = Runtime.getRuntime();
        StaticMetricsHolder.setGauge(getUsedMbKey(), (r.totalMemory() - r.freeMemory()) / 1024 / 1024);
        StaticMetricsHolder.setGauge(getMaxMbKey(), r.maxMemory() / 1024 / 1024);
        onReport();
    }

    protected abstract long getUsedMbKey();
    protected abstract long getMaxMbKey();
    protected abstract long getGcCountKey();
    protected abstract long getGcDurationKey();
    protected abstract long getGcHistoryStart();

    protected void onReport() {}
}
