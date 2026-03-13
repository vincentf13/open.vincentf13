package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.wire.Marshallable;
import net.openhft.chronicle.wire.WireIn;
import net.openhft.chronicle.wire.WireOut;
import org.agrona.DirectBuffer;

/**
 * 下單指令 (包裹原始 SBE 數據)
 */
@Data
public class OrderCreateCommand implements Marshallable {
    private long seq;
    private final PointerBytesStore sbePayload = new PointerBytesStore();

    @Override
    public void writeMarshallable(WireOut wire) {
        wire.write("seq").int64(seq);
        wire.write("data").bytes((net.openhft.chronicle.bytes.BytesStore) sbePayload);
    }

    @Override
    public void readMarshallable(WireIn wire) {
        seq = wire.read("seq").int64();
        net.openhft.chronicle.bytes.BytesStore bs = wire.read("data").bytesStore();
        if (bs != null) {
            sbePayload.set(bs.addressForRead(0), (int) bs.readRemaining());
        }
    }

    public void fillFrom(DirectBuffer buffer, int offset, int length, long seq) {
        this.seq = seq;
        this.sbePayload.set(buffer.addressOffset() + offset, length);
    }
}
