package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.MsgProgress;
import open.vincentf13.service.spot.model.WalProgress;
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
        Storage.self().metricsHistory().put(MetricsKey.CPU_ID_AERON_RECEIVER, (long) cpuId);
    }

    @Override
    protected void onStart() {
        // 從 Engine 的 WalProgress 獲取真正的處理位點
        WalProgress engineProgress = Storage.self().walMetadata().get(MetaDataKey.Wal.MACHING_ENGINE_POINT);
        if (engineProgress != null) {
            long seq = engineProgress.getLastProcessedMsgSeq();
            MsgProgress msgProg = new MsgProgress();
            msgProg.setLastProcessedSeq(seq);
            // 同步到接收器的 metadata，這樣 super.onStart() 就能讀到正確的恢復點
            metadata.put(metadataKey, msgProg);
            log.info("[AERON-RECEIVER] 已從 Engine 持久化位點恢復恢復位點: Seq={}", seq);
        }
        super.onStart();
    }

    @Override
    public void onMessage(DirectBuffer buffer, int offset, int length) {
        final int msgType = buffer.getInt(offset);

        // 自旋寫入，直到成功為止 (產生背壓，絕不丟包)
        while (!Storage.self().engineWorkQueue().write(msgType, buffer, offset, length)) {
            // 在極高壓下，稍微提示但不停下
            // Thread.onSpinWait(); // Java 9+ 提示 CPU 正在自旋
        }
    }}
