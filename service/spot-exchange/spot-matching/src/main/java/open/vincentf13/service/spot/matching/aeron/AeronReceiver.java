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
        try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
            Bytes<?> bytes = dc.wire().bytes();
            pointer.set(buffer.addressOffset() + offset, length);
            bytes.write(pointer);
        }
    }
}
