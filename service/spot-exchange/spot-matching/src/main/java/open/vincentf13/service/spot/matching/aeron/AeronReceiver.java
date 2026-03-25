package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.matching.engine.Engine;
import open.vincentf13.service.spot.model.MsgProgress;
import open.vincentf13.service.spot.model.WalProgress;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎 Aeron 接收器 (Worker 驅動版)
 繼承自基底類別，作為 Worker 啟動獨立執行緒，並負責驅動 Engine 的撮合與週期性任務。
 */
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

    @PostConstruct public void init() { 
        start("matching-worker");
    }

    @Override
    protected void onBind(int cpuId) {
        Storage.self().metricsHistory().put(new open.vincentf13.service.spot.infra.chronicle.LongValue(MetricsKey.CPU_ID_AERON_RECEIVER), new open.vincentf13.service.spot.infra.chronicle.LongValue((long) cpuId));
        engine.onBind(cpuId);
    }

    @Override
    protected void onStart() {
        // 1. 先讓 Engine 完成 cold start，確保業務完全就緒
        engine.onStart();

        // 2. 從 Engine 的落地點同步 Aeron 的接收進度
        WalProgress engineProgress = Storage.self().walMetadata().get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (engineProgress != null) {
            long seq = engineProgress.getLastProcessedMsgSeq();
            metadata.put(metadataKey, new MsgProgress(seq));
            log.info("[AERON-RECEIVER] 根據業務落地點對齊 Sequence: {}", seq);
        }

        // 3. 啟動基類 Aeron 訂閱
        super.onStart();
    }

    @Override
    protected int doWork() {
        // 1. 執行 Aeron 輪詢
        final int done = poll(engine, BATCH_SIZE);

        // 2. 驅動 Engine 週期性任務
        engine.tick(done);

        return done;
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
