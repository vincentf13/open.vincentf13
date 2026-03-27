package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.aeron.AeronConstants;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.WorkerMetrics;
import open.vincentf13.service.spot.matching.engine.Engine;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 撮合服務 Aeron 接收器 */
@Slf4j
@Component
public class AeronReceiver extends AbstractAeronReceiver {

    private final Engine engine;

    public AeronReceiver(Engine engine, Aeron aeron) {
        super(Storage.self().msgProgressMetadata(), MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE,
              AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID,
              AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        this.engine = engine;
    }

    @PostConstruct public void init() { workerStart("matching-receiver"); }

    @PreDestroy
    public void shutdown() {
        workerStop();
    }

    @Override
    protected int doWork() {
        int done = poll(null, AeronConstants.AERON_POLL_LIMIT);
        engine.onPollCycle(done);
        return done;
    }

    @Override
    protected void collectMetrics() {
        WorkerMetrics.reportThreadMetrics(
            MetricsKey.CPU_ID_AERON_RECEIVER,
            MetricsKey.CPU_ID_CURRENT_AERON_RECEIVER,
            MetricsKey.MATCHING_AERON_RECEVIER_WORKER_DUTY_CYCLE
        );
    }

    @Override
    protected void onMessage(org.agrona.DirectBuffer buffer, int offset, int length) {
        engine.onAeronMessage(buffer.getInt(offset), buffer, offset, length);
    }
}
