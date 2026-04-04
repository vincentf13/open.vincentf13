package open.vincentf13.service.spot.infra.thread;

import com.sun.jna.Library;
import com.sun.jna.Native;
import net.openhft.affinity.Affinity;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.BitSet;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CPU 親和性工具
 *
 * 職責：
 * 1. 核心綁定：acquireAndBind() 將當前線程鎖定至下一個可用物理核心
 * 2. 核心查詢：currentCpu() 取得當前執行的物理核心 ID
 * 3. 線程工廠：newThreadFactory() 建立自動綁核 + 指標上報的 ThreadFactory
 */
public class AffinityUtil {
    private static final Logger log = LoggerFactory.getLogger(AffinityUtil.class);
    private static final AtomicInteger assignedCount = new AtomicInteger(0);
    private static final ThreadLocal<Boolean> isBound = ThreadLocal.withInitial(() -> false);

    /** 自動分配並綁定一個可用核心，回傳核心 ID (-1 表示失敗) */
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
                log.info("[AFFINITY] {} 鎖定核心: {}", Thread.currentThread().getName(), targetCore);
                return targetCore;
            }
        } catch (Exception e) { log.error("[AFFINITY] 綁定��敗: {}", e.getMessage()); }
        return -1;
    }

    /** 取得當前線程的物理核心 ID */
    public static int currentCpu() {
        int cpu = Affinity.getCpu();
        if (cpu < 0 && System.getProperty("os.name").toLowerCase().contains("win")) {
            try { return Kernel32.INSTANCE.GetCurrentProcessorNumber(); }
            catch (Throwable ignored) {}
        }
        return cpu;
    }

    /**
     * 建立自動綁核的 ThreadFactory。
     * 每個新建線程啟動時自動綁核，並將 (historyKey, currentKey) 記錄至指標系統。
     */
    public static ThreadFactory newThreadFactory(String prefix, long[] historyKeys, long[] currentKeys) {
        AtomicInteger idx = new AtomicInteger(0);
        return r -> {
            int i = idx.getAndIncrement();
            Runnable task = () -> {
                try {
                    int cpuId = acquireAndBind();
                    if (i < historyKeys.length && i < currentKeys.length) {
                        StaticMetricsHolder.recordCpuId(historyKeys[i], currentKeys[i], cpuId);
                    }
                } catch (Throwable ignored) {}
                r.run();
            };
            return new Thread(task, prefix + "-" + (i + 1));
        };
    }

    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        int GetCurrentProcessorNumber();
    }

    private AffinityUtil() {}
}
