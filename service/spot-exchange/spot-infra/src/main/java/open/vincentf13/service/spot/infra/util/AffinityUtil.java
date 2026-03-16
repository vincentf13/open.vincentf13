package open.vincentf13.service.spot.infra.util;

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

    /**
     * 在進程允許的核心範圍內，自動分配並綁定一個核心
     * @return 綁定的物理核心 ID，失敗則返回 -1
     */
    public static int acquireAndBind() {
        try {
            BitSet affinity = Affinity.getAffinity();
            int totalAvailableInMask = affinity.cardinality();
            
            if (totalAvailableInMask <= 0) {
                log.warn("[AFFINITY] 無法獲取有效的 Affinity Mask");
                return -1;
            }

            // 根據已分配的 Worker 數量，循環選擇 Mask 中的可用核心
            int workerIdx = assignedWorkerCount.getAndIncrement();
            int targetIdx = workerIdx % totalAvailableInMask;
            
            int targetCore = -1;
            int currentFound = 0;
            for (int i = affinity.nextSetBit(0); i >= 0; i = affinity.nextSetBit(i + 1)) {
                if (currentFound == targetIdx) {
                    targetCore = i;
                    break;
                }
                currentFound++;
            }

            if (targetCore >= 0) {
                // 直接使用底層 Affinity.setAffinity 繞過 LockInventory 的檢測
                BitSet nextAffinity = new BitSet();
                nextAffinity.set(targetCore);
                Affinity.setAffinity(nextAffinity);
                
                log.info("[AFFINITY] 執行緒 [{}] 成功綁定至物理核心: {} (可用核心第 {}/{} 個)", 
                         Thread.currentThread().getName(), targetCore, targetIdx + 1, totalAvailableInMask);
                return targetCore;
            }
        } catch (Exception e) {
            log.error("[AFFINITY] 綁定過程中發生錯誤: {}", e.getMessage());
        }
        return -1;
    }
}
