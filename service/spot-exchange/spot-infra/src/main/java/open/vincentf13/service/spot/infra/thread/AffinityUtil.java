package open.vincentf13.service.spot.infra.thread;

import com.sun.jna.Library;
import com.sun.jna.Native;
import net.openhft.affinity.Affinity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CPU 親和性工具類 (AffinityUtil)
 * 職責：管理物理核心鎖定與即時狀態查詢。
 */
public class AffinityUtil {
    private static final Logger log = LoggerFactory.getLogger(AffinityUtil.class);
    private static final AtomicInteger assignedCount = new AtomicInteger(0);
    private static final ThreadLocal<Boolean> isBound = ThreadLocal.withInitial(() -> false);

    /** 自動分配並綁定一個可用核心 */
    public static int acquireAndBind() {
        if (isBound.get()) return -1;
        try {
            BitSet mask = Affinity.getAffinity();
            int totalAvailable = mask.cardinality();
            if (totalAvailable <= 0) return -1;

            int targetIdx = Math.abs(assignedCount.getAndIncrement() % totalAvailable);
            int targetCore = -1, currentIdx = 0;

            for (int i = mask.nextSetBit(0); i >= 0; i = mask.nextSetBit(i + 1)) {
                if (currentIdx++ == targetIdx) { targetCore = i; break; }
            }

            if (targetCore >= 0) {
                BitSet next = new BitSet();
                next.set(targetCore);
                Affinity.setAffinity(next);
                isBound.set(true);
                log.info("[AFFINITY] 執行緒 {} 成功鎖定核心: {}", Thread.currentThread().getName(), targetCore);
                return targetCore;
            }
        } catch (Exception e) { log.error("[AFFINITY] 綁定失敗: {}", e.getMessage()); }
        return -1;
    }

    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        int GetCurrentProcessorNumber();
    }

    /** 獲取目前執行緒真實運行的物理核心 ID */
    public static int currentCpu() {
        int cpu = Affinity.getCpu();
        if (cpu < 0 && System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                return Kernel32.INSTANCE.GetCurrentProcessorNumber();
            } catch (Throwable ignored) {}
        }
        return cpu;
    }
}
