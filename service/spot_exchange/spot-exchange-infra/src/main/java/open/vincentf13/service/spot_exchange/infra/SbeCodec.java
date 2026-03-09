package open.vincentf13.service.spot_exchange.infra;

import open.vincentf13.service.spot_exchange.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot_exchange.sbe.MessageHeaderEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import uk.co.real_logic.sbe.codec.java.DecoderFlyweight;
import uk.co.real_logic.sbe.codec.java.EncoderFlyweight;

/** 
  SBE 編解碼輔助工具 (線程安全且 Zero-GC 版本)
 */
public class SbeCodec {
    // --- 深度優化：使用 ThreadLocal 避免多線程競爭與對象建立 ---
    private static final ThreadLocal<MessageHeaderEncoder> HEADER_ENCODER = 
        ThreadLocal.withInitial(MessageHeaderEncoder::new);
    private static final ThreadLocal<MessageHeaderDecoder> HEADER_DECODER = 
        ThreadLocal.withInitial(MessageHeaderDecoder::new);

    public static int encode(MutableDirectBuffer buffer, int offset, EncoderFlyweight encoder) {
        MessageHeaderEncoder header = HEADER_ENCODER.get();
        encoder.wrapAndApplyHeader(buffer, offset, header);
        return MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
    }

    public static void decode(DirectBuffer buffer, int offset, DecoderFlyweight decoder) {
        MessageHeaderDecoder header = HEADER_DECODER.get();
        header.wrap(buffer, offset);
        decoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH, 
                     header.blockLength(), header.version());
    }
}
