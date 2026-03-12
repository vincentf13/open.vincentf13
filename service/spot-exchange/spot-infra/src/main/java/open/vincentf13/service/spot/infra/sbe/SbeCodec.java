package open.vincentf13.service.spot.infra.sbe;

import open.vincentf13.service.spot.sbe.*;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** 
  SBE 編解碼輔助工具 (線程安全且 Zero-GC 版本)
 */
public class SbeCodec {
    public static final int BLOCK_AND_VERSION_HEADER_SIZE = 8; // 2+2+2+2 for block, template, schema, version

    private static final ThreadLocal<MessageHeaderEncoder> HEADER_ENCODER = ThreadLocal.withInitial(MessageHeaderEncoder::new);
    private static final ThreadLocal<MessageHeaderDecoder> HEADER_DECODER = ThreadLocal.withInitial(MessageHeaderDecoder::new);

    public static int encode(MutableDirectBuffer buffer, int offset, OrderCreateEncoder encoder) {
        HEADER_ENCODER.get().wrap(buffer, offset)
                .blockLength(encoder.sbeBlockLength())
                .templateId(encoder.sbeTemplateId())
                .schemaId(encoder.sbeSchemaId())
                .version(encoder.sbeSchemaVersion());
        return MessageHeaderEncoder.ENCODED_LENGTH + encoder.wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH).encodedLength();
    }

    public static int encode(MutableDirectBuffer buffer, int offset, ExecutionReportEncoder encoder) {
        HEADER_ENCODER.get().wrap(buffer, offset)
                .blockLength(encoder.sbeBlockLength())
                .templateId(encoder.sbeTemplateId())
                .schemaId(encoder.sbeSchemaId())
                .version(encoder.sbeSchemaVersion());
        return MessageHeaderEncoder.ENCODED_LENGTH + encoder.wrap(buffer, offset + MessageHeaderEncoder.ENCODED_LENGTH).encodedLength();
    }

    public static void decode(DirectBuffer buffer, int offset, OrderCreateDecoder decoder) {
        MessageHeaderDecoder header = HEADER_DECODER.get();
        header.wrap(buffer, offset);
        decoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version());
    }

    public static void decode(DirectBuffer buffer, int offset, ExecutionReportDecoder decoder) {
        MessageHeaderDecoder header = HEADER_DECODER.get();
        header.wrap(buffer, offset);
        decoder.wrap(buffer, offset + MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version());
    }
}
