package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * 撤單指令 (SBE 封裝版)
 */
@Data
public class OrderCancelCommand implements BytesMarshallable {
    private long seq;
    private final PointerBytesStore sbePayload = new PointerBytesStore();

    public void encode(long seq, long timestamp, long userId, long orderId) {
        this.seq = seq;
        ThreadContext ctx = ThreadContext.get();
        UnsafeBuffer sbeBuffer = ctx.getScratchBuffer().wrapForWrite();
        int sbeLen = SbeCodec.encode(sbeBuffer, ctx.getOrderCancelEncoder()
                .timestamp(timestamp)
                .userId(userId)
                .orderId(orderId));
        this.fillFrom(sbeBuffer, 0, sbeLen);
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(seq);
        long len = sbePayload.readRemaining();
        bytes.writeStopBit(len);
        if (len > 0) {
            bytes.write(sbePayload);
        }
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        seq = bytes.readLong();
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
