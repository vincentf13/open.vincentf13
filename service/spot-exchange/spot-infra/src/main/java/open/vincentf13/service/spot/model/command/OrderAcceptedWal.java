package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import org.agrona.DirectBuffer;

@Data
public class OrderAcceptedWal implements BytesMarshallable {
    private long matchingSeq;
    private final PointerBytesStore sbePayload = new PointerBytesStore();

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(matchingSeq);
        long len = sbePayload.readRemaining();
        bytes.writeStopBit(len);
        if (len > 0) {
            bytes.write(sbePayload);
        }
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        matchingSeq = bytes.readLong();
        int len = (int) bytes.readStopBit();
        if (len > 0) {
            long address = bytes.addressForRead(bytes.readPosition());
            sbePayload.set(address, len);
            bytes.readSkip(len);
        } else {
            sbePayload.set(0, 0);
        }
    }

    public void fillFrom(DirectBuffer buffer, int offset, int length) {
        this.sbePayload.set(buffer.addressOffset() + offset, length);
    }
}
