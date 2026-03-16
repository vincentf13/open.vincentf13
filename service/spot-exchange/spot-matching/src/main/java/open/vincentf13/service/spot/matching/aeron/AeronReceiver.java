package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.alloc.OffHeapUtil;
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
        super(aeron, Storage.self().engineReceiverWal(), Storage.self().msgProgressMetadata(),
              MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE,
              AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID,
              AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("core-command-receiver"); }

    @Override
    public void onMessage(DirectBuffer buffer, int offset, int length) {
        final ThreadContext ctx = ThreadContext.get();
        final PointerBytesStore pointer = ctx.getReusablePointer();
        final long aeronSrcAddress = OffHeapUtil.getAddress(buffer, offset);
        pointer.set(aeronSrcAddress, length);

        // 回歸標準 Wire 模式寫入，確保與 Gateway WAL 格式完全一致
        try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
            net.openhft.chronicle.bytes.Bytes<?> bytes = dc.wire().bytes();
            // 寫入長度 + 數據內容
            bytes.writeInt(length);
            bytes.write(pointer);
            log.info("[AERON-RECEIVER] 訊息已寫入 Engine WAL, index={}, len={}", dc.index(), length);
        }
    }
}
