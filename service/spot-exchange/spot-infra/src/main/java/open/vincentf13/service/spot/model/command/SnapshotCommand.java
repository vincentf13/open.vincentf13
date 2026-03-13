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
 * 快照指令 (SBE 封裝版)
 */
@Data
public class SnapshotCommand implements BytesMarshallable {
    private long seq;
    private final PointerBytesStore pointBytesStore = new PointerBytesStore();

    /** 編碼並填充 SBE 載體 */
    public void encode(long seq, long timestamp) {
        this.seq = seq;
        int sbeLen = SbeCodec.encodeSnapshot(timestamp);
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
