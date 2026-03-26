package open.vincentf13.service.spot.ws.metrics;

import open.vincentf13.service.spot.infra.Constants.MetricsKey;
import open.vincentf13.service.spot.infra.metrics.AbstractJvmReporter;
import org.springframework.stereotype.Component;

/**
 * 網關服務 JVM 指標回報器
 */
@Component
public class GatewayJvmReporter extends AbstractJvmReporter {

    @Override protected long getUsedMbKey() { return MetricsKey.GATEWAY_JVM_USED_MB; }
    @Override protected long getMaxMbKey() { return MetricsKey.GATEWAY_JVM_MAX_MB; }
    @Override protected long getGcCountKey() { return MetricsKey.GATEWAY_GC_COUNT; }
    @Override protected long getGcDurationKey() { return MetricsKey.GATEWAY_GC_LAST_DURATION_MS; }
}
