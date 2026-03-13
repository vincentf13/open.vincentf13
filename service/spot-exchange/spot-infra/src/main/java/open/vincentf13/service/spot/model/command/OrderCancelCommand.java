package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.alloc.SbeCodec;
import org.agrona.DirectBuffer;

/**
 * 撤單指令 (SBE 封裝版)
 */
@Data
public class OrderCancelCommand implements BytesMarshallable {
    private long seq;
    private final PointerBytesStore pointBytesStore = new PointerBytesStore();

    /** 編碼並填充 SBE 載體 */
    public void encode(long seq, long timestamp, long userId, long orderId) {
        this.seq = seq;
        int sbeLen = SbeCodec.encodeOrderCancel(timestamp, userId, orderId);
        this.fillFrom(ThreadContext.get().getScratchBuffer().buffer(), 0, sbeLen);
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(seq);
        long len = pointBytesStore.readRemaining();
        bytes.writeStopBit(len);
        if (len > 0) {
            bytes.write(pointBytesStore);
        }
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        seq = bytes.readLong();
        int len = (int) bytes.readStopBit();
        if (len > 0) {
            long address = bytes.addressForRead(bytes.readPosition());
            pointBytesStore.set(address, len);
            bytes.readSkip(len);
        } else {
            pointBytesStore.set(0, 0);
        }
    }

    public void fillFrom(open.vincentf13.service.spot.infra.alloc.aeron.AbstractAeronAlloc<?> aeron) {
        this.seq = aeron.readSeq();
        this.pointBytesStore.set(aeron.getBufferAddress() + aeron.getPayloadOffset(), aeron.getPayloadLength());
    }

    public void fillFrom(DirectBuffer buffer, int offset, int length) {
        this.pointBytesStore.set(buffer.addressOffset() + offset, length);
    }
}
