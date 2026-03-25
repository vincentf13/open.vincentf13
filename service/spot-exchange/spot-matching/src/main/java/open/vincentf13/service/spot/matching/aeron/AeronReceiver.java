package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.WorkerMonitor;
import open.vincentf13.service.spot.matching.engine.Engine;
import open.vincentf13.service.spot.model.MsgProgress;
import open.vincentf13.service.spot.model.WalProgress;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 撮合引擎 Aeron 接收器 (Worker 驅動版) */
@Slf4j
@Component
public class AeronReceiver extends AbstractAeronReceiver {
    private final Engine engine;
    private static final int BATCH_SIZE = Matching.ENGINE_BATCH_SIZE;

    public AeronReceiver(Aeron aeron, Engine engine) {
        super(Storage.self().msgProgressMetadata(),
              MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE,
              AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID,
              AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        this.engine = engine;
    }

    @PostConstruct public void init() { workerStart("matching-worker"); }

    @Override
    protected void onStart() {
        engine.onStart();
        WalProgress engineProgress = Storage.self().walMetadata().get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (engineProgress != null) {
            long seq = engineProgress.getLastProcessedMsgSeq();
            metadata.put(metadataKey, new MsgProgress(seq));
            log.info("[AERON-RECEIVER] 根據業務落地點對齊 Sequence: {}", seq);
        }
        super.onStart();
    }

    @Override
    protected int doWork() {
        final int done = poll(engine, BATCH_SIZE);
        engine.tick(done);
        return done;
    }

    @Override
    protected void collectMetrics() {
        // 僅回報執行緒級別指標
        WorkerMonitor.reportThreadMetrics(MetricsKey.CPU_ID_AERON_RECEIVER, MetricsKey.MATCHING_AERON_RECEVIER_WORKER_DUTY_CYCLE);
    }

    @Override
    protected void onStop() {
        engine.onStop();
        super.onStop();
    }

    @Override
    public void onMessage(DirectBuffer buffer, int offset, int length) {
        log.warn("[AERON-RECEIVER] 意外進入備援 Handler: {}", length);
    }
}
