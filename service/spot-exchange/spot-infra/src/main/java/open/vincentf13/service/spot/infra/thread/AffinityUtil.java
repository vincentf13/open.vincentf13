package open.vincentf13.service.spot.infra.thread;

import net.openhft.affinity.Affinity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 優化的 CPU 親和性工具類
 * 專門解決 Windows 環境下 ProcessorAffinity 導致 AffinityLock 邊界檢查失敗的問題
 */
public class AffinityUtil {
    private static final Logger log = LoggerFactory.getLogger(AffinityUtil.class);
    
    // 用於在本 JVM 內部遞增分配核心，確保不同 Worker 盡量分開
    private static final AtomicInteger assignedWorkerCount = new AtomicInteger(0);
    
    // 防止同一個執行緒重複執行綁定邏輯 (Netty Worker 等執行緒池場景)
    private static final ThreadLocal<Boolean> isBound = ThreadLocal.withInitial(() -> false);

    /**
     * 在進程允許的核心範圍內，自動分配並綁定一個核心
     * @return 綁定的物理核心 ID，失敗則返回 -1
     */
    public static int acquireAndBind() {
        return acquireAndBind(-1);
    }

    /**
     * 綁定指定核心，或根據 Mask 自動循環分配
     * @param preferredCore 偏好的核心 ID，傳入 -1 則自動分配
     * @return 實際綁定的核心 ID
     */
    public static int acquireAndBind(int preferredCore) {
        if (isBound.get()) {
            return -1; // 已綁定過，不再重複綁定
        }
        try {
            BitSet affinity = Affinity.getAffinity();
            int totalAvailableInMask = affinity.cardinality();

            if (totalAvailableInMask <= 0) {
                log.warn("[AFFINITY] 無法獲取有效的 Affinity Mask，跳過綁定");
                return -1;
            }

            int targetCore = -1;
            // 如果指定了核心且在 Mask 範圍內，優先使用
            if (preferredCore >= 0 && affinity.get(preferredCore)) {
                targetCore = preferredCore;
            } else {
                // --- 核心優化：基於可用核心列表的循環分配 ---
                int[] availableCores = new int[totalAvailableInMask];
                int count = 0;
                for (int i = affinity.nextSetBit(0); i >= 0; i = affinity.nextSetBit(i + 1)) {
                    availableCores[count++] = i;
                }

                // 取模分配，確保即使 Worker 數量超過核心數也能均勻分佈
                int idx = Math.abs(assignedWorkerCount.getAndIncrement() % totalAvailableInMask);
                targetCore = availableCores[idx];
            }

            if (targetCore >= 0) {
                BitSet nextAffinity = new BitSet();
                nextAffinity.set(targetCore);
                Affinity.setAffinity(nextAffinity);
                
                isBound.set(true);

                log.info("[AFFINITY] 執行緒 [{}] 成功綁定至物理核心: {} (Mask 總數: {})", 
                         Thread.currentThread().getName(), targetCore, totalAvailableInMask);
                return targetCore;
            }
        } catch (Exception e) {
            log.error("[AFFINITY] 綁定過程中發生錯誤: {}", e.getMessage());
        }
        return -1;
    }
}
