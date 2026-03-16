package open.vincentf13.service.spot.infra.util;

import net.openhft.affinity.Affinity;
import net.openhft.affinity.AffinityLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;

/**
 * CPU 親和性與綁核工具類 (Affinity Utility)
 */
public class AffinityUtil {
    private static final Logger log = LoggerFactory.getLogger(AffinityUtil.class);

    /**
     * 在當前進程被允許的 CPU 核心範圍內，嘗試獲取第一個可用的獨佔鎖。
     * 適用於低延遲工作者執行緒或 App 主執行緒。
     *
     * @return 成功鎖定的 AffinityLock 對象，若失敗則返回空 (null)
     */
    public static AffinityLock acquireLockInAffinity() {
        try {
            BitSet affinity = Affinity.getAffinity();
            for (int i = affinity.nextSetBit(0); i >= 0; i = affinity.nextSetBit(i + 1)) {
                try {
                    AffinityLock lock = AffinityLock.acquireLock(i);
                    if (lock.cpuId() >= 0) {
                        return lock;
                    }
                } catch (Exception ignored) {
                    // 核心可能已被其他執行緒佔用，嘗試下一個
                }
            }
        } catch (Exception e) {
            log.warn("無法獲取 CPU Affinity 資訊或鎖定核心: {}", e.getMessage());
        }
        return null;
    }
}
