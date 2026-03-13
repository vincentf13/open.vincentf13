package open.vincentf13.service.spot.infra.alloc.aeron;

import open.vincentf13.service.spot.infra.Constants.MsgType;
import org.agrona.DirectBuffer;

/**
 * Aeron 下單訊息
 */
public class AeronOrderCreate extends AbstractAeronAlloc<AeronOrderCreate> {
    
    /** 打包下單訊息 (假設 sbeBuffer 已經是正確的 View) */
    public void write(long seq, DirectBuffer sbeBuffer) {
        writeHeader(MsgType.ORDER_CREATE, seq);
        mutableBuffer.putBytes(offset + HEADER_LENGTH, sbeBuffer, 0, (int) sbeBuffer.capacity());
    }
}
