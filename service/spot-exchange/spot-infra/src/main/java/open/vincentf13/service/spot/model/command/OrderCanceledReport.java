package open.vincentf13.service.spot.model.command;

import lombok.Data;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.PointerBytesStore;
import open.vincentf13.service.spot.infra.alloc.SbeCodec;
import org.agrona.DirectBuffer;

/**
 * 訂單撤單回報
 */
@Data
public class OrderCanceledReport implements BytesMarshallable {
    private long gatewaySeq;
    private final PointerBytesStore pointBytesStore = new PointerBytesStore();

    public open.vincentf13.service.spot.sbe.ExecutionReportDecoder decode() {
        return SbeCodec.decodeExecutionReport(pointBytesStore);
    }

    public void encode(long timestamp, long userId, long orderId, long filledQuantity, long clientOrderId) {
        int length = SbeCodec.encodeToScratchCanceledReport(timestamp, userId, orderId, filledQuantity, clientOrderId);
        fillFromScratch(length);
    }

    public void fillFrom(open.vincentf13.service.spot.infra.alloc.aeron.AbstractAeronAlloc<?> aeron) {
        this.gatewaySeq = aeron.readSeq();
        this.pointBytesStore.set(aeron.getBufferAddress() + aeron.getPayloadOffset(), aeron.getPayloadLength());
    }

    public void fillFrom(DirectBuffer buffer, int offset, int length) {
        this.pointBytesStore.set(buffer.addressOffset() + offset, length);
    }

    public void fillFromScratch(int length) {
        fillFrom(open.vincentf13.service.spot.infra.alloc.ThreadContext.get().getScratchBuffer().buffer(), 0, length);
    }

    @Override
    public void writeMarshallable(BytesOut<?> bytes) {
        bytes.writeLong(gatewaySeq);
        long len = pointBytesStore.readRemaining();
        bytes.writeStopBit(len);
        if (len > 0) bytes.write(pointBytesStore);
    }

    @Override
    public void readMarshallable(BytesIn<?> bytes) {
        gatewaySeq = bytes.readLong();
        int len = (int) bytes.readStopBit();
        if (len > 0) {
            long address = bytes.addressForRead(bytes.readPosition());
            pointBytesStore.set(address, len);
            bytes.readSkip(len);
        } else {
            pointBytesStore.set(0, 0);
        }
    }
}
