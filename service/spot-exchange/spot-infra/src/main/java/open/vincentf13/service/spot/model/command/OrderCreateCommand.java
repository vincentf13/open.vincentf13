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

    /** 編碼並填充 SBE 載體 */
    public void encode(long seq, long timestamp, long userId, int symbolId, long price, long qty, Side side, long clientOrderId) {
        this.seq = seq;
        int sbeLen = SbeCodec.encodeOrderCreate(timestamp, userId, symbolId, price, qty, side, clientOrderId);
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

    public void fillFrom(DirectBuffer buffer, int offset, int length) {
        this.pointBytesStore.set(buffer.addressOffset() + offset, length);
    }
}
