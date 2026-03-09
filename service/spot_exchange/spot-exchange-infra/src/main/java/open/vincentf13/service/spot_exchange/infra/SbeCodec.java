package open.vincentf13.service.spot_exchange.infra;

import open.vincentf13.service.spot_exchange.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot_exchange.sbe.MessageHeaderEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import uk.co.real_logic.sbe.codec.java.DecoderFlyweight;
import uk.co.real_logic.sbe.codec.java.EncoderFlyweight;

/** 
  SBE 編解碼輔助工具
 */
public class SbeCodec {
    private static final MessageHeaderEncoder HEADER_ENCODER = new MessageHeaderEncoder();
    private static final MessageHeaderDecoder HEADER_DECODER = new MessageHeaderDecoder();

    /** 
      編碼並套用 Header
     */
    public static int encode(MutableDirectBuffer buffer, int offset, EncoderFlyweight encoder) {
        encoder.wrapAndApplyHeader(buffer, offset, HEADER_ENCODER);
        return MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
    }

    /** 
      解碼 Body
     */
    public static void decode(DirectBuffer buffer, int offset, DecoderFlyweight decoder) {
        HEADER_DECODER.wrap(buffer, offset);
        decoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH, 
                     HEADER_DECODER.blockLength(), HEADER_DECODER.version());
    }
}
