package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.alloc.SbeCodec;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.DirectBuffer;

/**
 * 下單指令 (完全封裝版)
 */
@Data
public class OrderCreateCommand implements BytesMarshallable {
    private long seq;
    private final PointerBytesStore pointBytesStore = new PointerBytesStore();

    public void fillFromScratch(int length) {
        fillFrom(open.vincentf13.service.spot.infra.alloc.ThreadContext.get().getScratchBuffer().buffer(), 0, length);
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
