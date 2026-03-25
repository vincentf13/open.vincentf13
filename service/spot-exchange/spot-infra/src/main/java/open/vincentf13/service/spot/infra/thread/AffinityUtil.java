package open.vincentf13.service.spot.infra.thread;

import net.openhft.affinity.Affinity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CPU 親和性工具類 (AffinityUtil)
 * 職責：在進程允許的核心範圍內自動分配並鎖定物理核心，確保高性能執行緒不被切換。
 */
public class AffinityUtil {
    private static final Logger log = LoggerFactory.getLogger(AffinityUtil.class);
    
    /** 全局分配計數器，用於在可用核心間進行循環分配 */
    private static final AtomicInteger assignedCount = new AtomicInteger(0);
    
    /** 執行緒局部標記，防止同一個執行緒重複執行綁定邏輯 */
    private static final ThreadLocal<Boolean> isBound = ThreadLocal.withInitial(() -> false);

    /**
     * 自動分配並綁定一個可用核心。
     * 邏輯：從目前進程允許的 CPU 掩碼 (Mask) 中，按順序選取一個物理核心進行綁定。
     * 
     * @return 實際綁定的物理核心 ID，失敗則返回 -1
     */
    public static int acquireAndBind() {
        if (isBound.get()) return -1;
        
        try {
            // 1. 獲取目前進程可用的 CPU 核心掩碼
            BitSet mask = Affinity.getAffinity();
            int totalAvailable = mask.cardinality();
            if (totalAvailable <= 0) return -1;

            // 2. 透過全局計數器決定要分配第幾個可用核心 (循環分配策略)
            int targetIdx = Math.abs(assignedCount.getAndIncrement() % totalAvailable);
            int targetCore = -1;
            int currentIdx = 0;

            // 3. 在掩碼中尋找第 targetIdx 個被設置的核心位點
            for (int i = mask.nextSetBit(0); i >= 0; i = mask.nextSetBit(i + 1)) {
                if (currentIdx++ == targetIdx) {
                    targetCore = i;
                    break;
                }
            }

            // 4. 執行物理綁定
            if (targetCore >= 0) {
                BitSet next = new BitSet();
                next.set(targetCore);
                Affinity.setAffinity(next);
                isBound.set(true);

                log.info("[AFFINITY] 執行緒 {} 成功鎖定核心: {}", Thread.currentThread().getName(), targetCore);
                return targetCore;
            }
        } catch (Exception e) {
            log.error("[AFFINITY] 核心綁定異常: {}", e.getMessage());
        }
        return -1;
    }
}
