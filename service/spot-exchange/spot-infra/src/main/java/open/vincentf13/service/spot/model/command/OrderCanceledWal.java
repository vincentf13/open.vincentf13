package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import org.agrona.DirectBuffer;

@Data
public class OrderCanceledWal implements BytesMarshallable {
    private long matchingSeq;
    private final PointerBytesStore pointBytesStore = new PointerBytesStore();

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(matchingSeq);
        long len = pointBytesStore.readRemaining();
        bytes.writeStopBit(len);
        if (len > 0) {
            bytes.write(pointBytesStore);
        }
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        matchingSeq = bytes.readLong();
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
        this.matchingSeq = aeron.readSeq();
        this.pointBytesStore.set(aeron.getBufferAddress() + aeron.getPayloadOffset(), aeron.getPayloadLength());
    }

    public void fillFrom(DirectBuffer buffer, int offset, int length) {
        this.pointBytesStore.set(buffer.addressOffset() + offset, length);
    }
}
