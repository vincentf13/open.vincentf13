package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.aeron.AbstractAeronReceiver;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.agrona.DirectBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎 Aeron 接收器 (Raw WAL 寫入版)
 */
@Slf4j
@Component
public class AeronReceiver extends AbstractAeronReceiver {
    private final PointerBytesStore pointer = new PointerBytesStore();

    public AeronReceiver(Aeron aeron) {
        super(aeron, Storage.self().engineReceiverWal(), Storage.self().msgProgressMetadata(),
              MetaDataKey.MsgProgress.MATCHING_ENGINE_RECEIVE,
              AeronChannel.GATEWAY_URL, AeronChannel.DATA_STREAM_ID,
              AeronChannel.MATCHING_URL, AeronChannel.CONTROL_STREAM_ID);
    }

    @PostConstruct public void init() { start("core-command-receiver"); }

    @Override
    public void onMessage(DirectBuffer buffer, int offset, int length) {
        // 直接寫入原始字節到 WAL，無 Wire Key
        try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
            Bytes<?> bytes = dc.wire().bytes();
            
            // 來自 Aeron 的原始數據物理地址
            final long aeronSrcAddress = buffer.addressOffset() + offset;
            
            pointer.set(aeronSrcAddress, length);
            bytes.write(pointer);
        }
    }
}
