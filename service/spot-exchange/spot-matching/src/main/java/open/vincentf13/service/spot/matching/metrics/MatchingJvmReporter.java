package open.vincentf13.service.spot.matching.metrics;

import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.AbstractJvmReporter;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import org.springframework.stereotype.Component;

/**
 * 撮合服務 JVM 指標回報器
 */
@Component
public class MatchingJvmReporter extends AbstractJvmReporter {

    @Override protected long getUsedMbKey() { return MetricsKey.MATCHING_JVM_USED_MB; }
    @Override protected long getMaxMbKey() { return MetricsKey.MATCHING_JVM_MAX_MB; }
    @Override protected long getGcCountKey() { return MetricsKey.MATCHING_GC_COUNT; }
    @Override protected long getGcDurationKey() { return MetricsKey.MATCHING_GC_LAST_DURATION_MS; }

    @Override
    protected void onReport() {
        var c = StaticMetricsHolder.values().get(MetricsKey.MATCH_COUNT);
        Storage.self().tpsHistory().put(System.currentTimeMillis(), c != null ? c.get() : 0L);
    }
}
