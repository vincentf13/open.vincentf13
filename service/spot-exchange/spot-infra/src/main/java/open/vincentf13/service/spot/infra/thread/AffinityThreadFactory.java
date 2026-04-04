package open.vincentf13.service.spot.infra.thread;

import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CPU 親和性線程工廠
 *
 * 每個新建線程啟動時自動綁定至下一個可用 CPU 核心，
 * 並將 (historyKey, currentKey) 對記錄至指標系統。
 */
public class AffinityThreadFactory implements ThreadFactory {
    private final String prefix;
    private final long[] historyKeys;
    private final long[] currentKeys;
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * @param prefix      線程名稱前綴
     * @param historyKeys 每個線程對應的 CPU 歷史指標 key (按建立順序)
     * @param currentKeys 每個線程對應的 CPU 即時指標 key (按建立順序)
     */
    public AffinityThreadFactory(String prefix, long[] historyKeys, long[] currentKeys) {
        this.prefix = prefix;
        this.historyKeys = historyKeys;
        this.currentKeys = currentKeys;
    }

    @Override
    public Thread newThread(Runnable r) {
        int idx = counter.getAndIncrement();
        Runnable task = () -> {
            try {
                int cpuId = AffinityUtil.acquireAndBind();
                if (idx < historyKeys.length && idx < currentKeys.length) {
                    StaticMetricsHolder.recordCpuId(historyKeys[idx], currentKeys[idx], cpuId);
                }
            } catch (Throwable ignored) {}
            r.run();
        };
        return new Thread(task, prefix + "-" + (idx + 1));
    }
}
