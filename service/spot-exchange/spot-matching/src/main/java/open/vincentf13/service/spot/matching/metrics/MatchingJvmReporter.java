package open.vincentf13.service.spot.matching.metrics;

import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.metrics.AbstractJvmReporter;
import org.springframework.stereotype.Component;

/**
 * 撮合服務 JVM 指標回報器
 *
 * JVM 記憶體與 GC 資料寫入 StaticMetricsHolder (in-memory)，
 * 由 MetricsWriter 統一異步批次刷入 ChronicleMap。
 */
@Component
public class MatchingJvmReporter extends AbstractJvmReporter {

    @Override protected long getUsedMbKey()      { return MetricsKey.MATCHING_JVM_USED_MB; }
    @Override protected long getMaxMbKey()       { return MetricsKey.MATCHING_JVM_MAX_MB; }
    @Override protected long getGcCountKey()     { return MetricsKey.MATCHING_GC_COUNT; }
    @Override protected long getGcDurationKey()  { return MetricsKey.MATCHING_GC_LAST_DURATION_MS; }
    @Override protected long getGcHistoryStart() { return MetricsKey.MATCHING_GC_HISTORY_START; }
}
