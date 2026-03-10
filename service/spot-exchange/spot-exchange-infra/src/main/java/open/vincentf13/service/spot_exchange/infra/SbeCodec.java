package open.vincentf13.service.spot_exchange.infra;

import open.vincentf13.service.spot_exchange.sbe.*;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** 
  SBE 編解碼輔助工具 (線程安全且 Zero-GC 版本)
 */
public class SbeCodec {
    private static final ThreadLocal<MessageHeaderEncoder> HEADER_ENCODER = 
        ThreadLocal.withInitial(MessageHeaderEncoder::new);
    private static final ThreadLocal<MessageHeaderDecoder> HEADER_DECODER = 
        ThreadLocal.withInitial(MessageHeaderDecoder::new);

    public static int encode(MutableDirectBuffer buffer, int offset, OrderCreateEncoder encoder) {
        MessageHeaderEncoder header = HEADER_ENCODER.get();
        encoder.wrapAndApplyHeader(buffer, offset, header);
        return MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
    }

    public static int encode(MutableDirectBuffer buffer, int offset, ExecutionReportEncoder encoder) {
        MessageHeaderEncoder header = HEADER_ENCODER.get();
        encoder.wrapAndApplyHeader(buffer, offset, header);
        return MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
    }

    public static void decode(DirectBuffer buffer, int offset, OrderCreateDecoder decoder) {
        MessageHeaderDecoder header = HEADER_DECODER.get();
        header.wrap(buffer, offset);
        decoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH, 
                     header.blockLength(), header.version());
    }

    public static void decode(DirectBuffer buffer, int offset, OrderCancelDecoder decoder) {
        MessageHeaderDecoder header = HEADER_DECODER.get();
        header.wrap(buffer, offset);
        decoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH, 
                     header.blockLength(), header.version());
    }
    
    public static void decode(DirectBuffer buffer, int offset, ExecutionReportDecoder decoder) {
        MessageHeaderDecoder header = HEADER_DECODER.get();
        header.wrap(buffer, offset);
        decoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH, 
                     header.blockLength(), header.version());
    }
}
