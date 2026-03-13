package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;
import org.agrona.DirectBuffer;

@Data
public class OrderRejectedWal implements Marshallable {
    private long matchingSeq;
    private final PointerBytesStore sbePayload = new PointerBytesStore();

    @Override
    public void writeMarshallable(WireOut wire) {
        wire.write("matchingSeq").int64(matchingSeq);
        wire.write("data").bytes((net.openhft.chronicle.bytes.BytesStore) sbePayload);
    }

    @Override
    public void readMarshallable(WireIn wire) {
        matchingSeq = wire.read("matchingSeq").int64();
        net.openhft.chronicle.bytes.BytesStore bs = wire.read("data").bytesStore();
        if (bs != null) {
            sbePayload.set(bs.addressForRead(0), (int) bs.readRemaining());
        }
    }

    public void fillFrom(DirectBuffer buffer, int offset, int length, long seq) {
        this.matchingSeq = seq;
        this.sbePayload.set(buffer.addressOffset() + offset, length);
    }
}
