package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎 Aeron 接收器 (Raw WAL 寫入版)
 繼承自基類以獲得 RESUME 信號發送、Sequence 冪等檢查與連續性校驗能力
 */
@Slf4j
@Component
public class AeronReceiver extends AbstractAeronReceiver {

    public AeronReceiver(Aeron aeron) {
        super(aeron, Storage.self().msgProgressMetadata(),
              MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE,
              AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID,
              AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("core-command-receiver"); }

    @Override
    protected void onBind(int cpuId) {
        Storage.self().metricsHistory().put(Storage.KEY_CPU_ID_AERON_RECEIVER, (long) cpuId);
    }

    private long localRecvCount = 0;
    private static final int METRICS_BATCH_SIZE = 5000;

    @Override
    public void onMessage(DirectBuffer buffer, int offset, int length) {
        // 直接將 Aeron 原始數據寫入 Engine Work RingBuffer (零拷貝)
        boolean success = Storage.self().engineWorkQueue().write(MsgType.MATCHING_ENGINE_COMMAND, buffer, offset, length);
        
        if (success) {
            localRecvCount++;
            if (localRecvCount >= METRICS_BATCH_SIZE) {
                final long batch = localRecvCount;
                Storage.self().metricsHistory().compute(Storage.KEY_AERON_RECV_COUNT, (k, v) -> v == null ? batch : v + batch);
                localRecvCount = 0;
            }
            log.debug("[AERON-RECEIVER] 數據已寫入 Engine RingBuffer, len={}", length);
        } else {
            // RingBuffer 滿了 (Backpressure)，雖然 ManyToOneRingBuffer.write() 是非阻塞的，但這裡需要警示
            log.error("[AERON-RECEIVER] Engine RingBuffer is FULL! Message dropped, len={}", length);
            Storage.self().metricsHistory().compute(Storage.KEY_AERON_BACKPRESSURE, (k, v) -> v == null ? 1L : v + 1);
        }
    }
}
