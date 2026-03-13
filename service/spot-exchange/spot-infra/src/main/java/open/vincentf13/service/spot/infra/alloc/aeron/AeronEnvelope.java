package open.vincentf13.service.spot.infra.alloc.aeron;

import org.agrona.DirectBuffer;

/**
 * 通用 Aeron 訊息封包 (Envelope)
 * 用於處理僅包含 Header 與原始位元組載體的訊息
 */
public class AeronEnvelope extends AbstractAeronAlloc<AeronEnvelope> {
    
    /** 打包通用封包 */
    public void write(int msgType, long seq, DirectBuffer payload, int pOffset, int pLength) {
        writeHeader(msgType, seq);
        if (pLength > 0) {
            mutableBuffer.putBytes(offset + HEADER_LENGTH, payload, pOffset, pLength);
        }
    }
}
