package open.vincentf13.service.spot.infra.alloc.aeron;

import open.vincentf13.service.spot.infra.Constants.MsgType;
import org.agrona.DirectBuffer;

/**
 * Aeron 下單訊息
 */
public class AeronOrderCreate extends AbstractAeronAlloc<AeronOrderCreate> {
    
    /** 打包下單訊息 (需先調用 wrap) */
    public void write(long seq, DirectBuffer sbeBuffer, int sbeOffset, int sbeLength) {
        writeHeader(MsgType.ORDER_CREATE, seq);
        mutableBuffer.putBytes(offset + HEADER_LENGTH, sbeBuffer, sbeOffset, sbeLength);
    }
}
