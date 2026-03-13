package open.vincentf13.service.spot.model.Progress;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 網路消息處理進度 (MsgProgress)
 * 職責：持久化記錄 Aeron 網路消息流處理到的最後序號 (Sequence)
 */
@Data
public class MsgProgress implements BytesMarshallable {
    // 最後處理的來源端業務序號 (Logical Sequence)
    private long lastProcessedSeq = MSG_SEQ_NONE;

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(lastProcessedSeq);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        lastProcessedSeq = bytes.readLong();
    }
}
