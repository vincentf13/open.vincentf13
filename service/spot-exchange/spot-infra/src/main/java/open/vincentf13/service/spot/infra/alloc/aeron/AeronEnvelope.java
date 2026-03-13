package open.vincentf13.service.spot.infra.alloc.aeron;

import org.agrona.DirectBuffer;

/**
 * 通用 Aeron 訊息封包 (Envelope)
 */
public class AeronEnvelope extends AbstractAeronAlloc<AeronEnvelope> {
    
    /** 打包通用封包 (使用傳入 buffer 的容量作為長度) */
    public void write(int msgType, long seq, DirectBuffer payload) {
        writeHeader(msgType, seq);
        int pLength = (int) payload.capacity();
        if (pLength > 0) {
            mutableBuffer.putBytes(offset + HEADER_LENGTH, payload, 0, pLength);
        }
    }
}
