package open.vincentf13.service.spot.matching.metrics;

import io.micrometer.core.instrument.Metrics;
import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.chronicle.LongValue;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.AbstractJvmReporter;
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
        // 額外邏輯：同步 TPS 歷史 (非共用)
        final long now = System.currentTimeMillis();
        long totalMatch = (long) Metrics.globalRegistry.counter("spot.metric." + MetricsKey.MATCH_COUNT).count();
        Storage.self().tpsHistory().put(new LongValue(now), new LongValue(totalMatch));
    }
}
