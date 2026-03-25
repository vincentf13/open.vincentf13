package open.vincentf13.service.spot.infra.jvm;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * JVM 運行時工具類 (Jvm)
 * 職責：封裝 JVM 指標採集，並利用 ThreadLocal 實現執行緒級別的「有效循環比 (Duty Cycle)」監控。
 */
public class Jvm {

    private static final Runtime RUNTIME = Runtime.getRuntime();
    private static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getOperatingSystemMXBean();

    // --- ThreadLocal Duty Cycle Counters ---
    // [0]: totalCycles, [1]: workCycles
    private static final ThreadLocal<long[]> CYCLE_COUNTERS = ThreadLocal.withInitial(() -> new long[2]);

    /** 標記一輪循環開始 (由執行緒在 while 頂部呼叫) */
    public static void startCycle() {
        CYCLE_COUNTERS.get()[0]++;
    }

    /** 標記一輪循環結束，並記錄是否有實際工作 */
    public static void endCycle(boolean worked) {
        if (worked) {
            CYCLE_COUNTERS.get()[1]++;
        }
    }

    /** 獲取並重置目前執行緒的有效循環比 (單位: 0.01%，回傳 10000 代表 100%) */
    public static long getAndResetDutyCycle() {
        long[] counters = CYCLE_COUNTERS.get();
        long total = counters[0];
        long work = counters[1];
        if (total == 0) return 0;
        long dutyCycle = (work * 10000) / total;
        counters[0] = 0;
        counters[1] = 0;
        return dutyCycle;
    }

    /** 獲取目前 JVM 已使用的內存 (MB) */
    public static long usedMemoryMb() {
        return (RUNTIME.totalMemory() - RUNTIME.freeMemory()) / 1024 / 1024;
    }

    /** 獲取目前 JVM 的最大可用內存 (MB) */
    public static long maxMemoryMb() {
        return RUNTIME.maxMemory() / 1024 / 1024;
    }

    private Jvm() {}
}
