package open.vincentf13.service.spot.infra.sbe;

import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.sbe.*;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** 
  SBE 編解碼輔助工具 (極速簡化版)
 */
public class SbeCodec {
    public static final int BLOCK_AND_VERSION_HEADER_SIZE = 8;
    private static final int DEFAULT_OFFSET = 0; // 統一偏移量

    public static int encode(MutableDirectBuffer buffer, OrderCreateEncoder encoder) {
        return wrapHeader(buffer, encoder.sbeBlockLength(), encoder.sbeTemplateId(), encoder.sbeSchemaId(), encoder.sbeSchemaVersion())
                + encoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderEncoder.ENCODED_LENGTH).encodedLength();
    }

    public static int encode(MutableDirectBuffer buffer, AuthEncoder encoder) {
        return wrapHeader(buffer, encoder.sbeBlockLength(), encoder.sbeTemplateId(), encoder.sbeSchemaId(), encoder.sbeSchemaVersion())
                + encoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderEncoder.ENCODED_LENGTH).encodedLength();
    }

    public static int encode(MutableDirectBuffer buffer, OrderCancelEncoder encoder) {
        return wrapHeader(buffer, encoder.sbeBlockLength(), encoder.sbeTemplateId(), encoder.sbeSchemaId(), encoder.sbeSchemaVersion())
                + encoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderEncoder.ENCODED_LENGTH).encodedLength();
    }

    public static int encode(MutableDirectBuffer buffer, DepositEncoder encoder) {
        return wrapHeader(buffer, encoder.sbeBlockLength(), encoder.sbeTemplateId(), encoder.sbeSchemaId(), encoder.sbeSchemaVersion())
                + encoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderEncoder.ENCODED_LENGTH).encodedLength();
    }

    public static int encode(MutableDirectBuffer buffer, SnapshotEncoder encoder) {
        return wrapHeader(buffer, encoder.sbeBlockLength(), encoder.sbeTemplateId(), encoder.sbeSchemaId(), encoder.sbeSchemaVersion())
                + encoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderEncoder.ENCODED_LENGTH).encodedLength();
    }

    public static void decode(DirectBuffer buffer, SnapshotDecoder decoder) {
        MessageHeaderDecoder header = wrapHeader(buffer);
        decoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version());
    }

    public static int encode(MutableDirectBuffer buffer, ExecutionReportEncoder encoder) {
        return wrapHeader(buffer, encoder.sbeBlockLength(), encoder.sbeTemplateId(), encoder.sbeSchemaId(), encoder.sbeSchemaVersion())
                + encoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderEncoder.ENCODED_LENGTH).encodedLength();
    }

    private static int wrapHeader(MutableDirectBuffer buffer, int blockLength, int templateId, int schemaId, int version) {
        ThreadContext.get().getHeaderEncoder().wrap(buffer, DEFAULT_OFFSET)
                .blockLength(blockLength)
                .templateId(templateId)
                .schemaId(schemaId)
                .version(version);
        return MessageHeaderEncoder.ENCODED_LENGTH;
    }

    public static void decode(DirectBuffer buffer, OrderCreateDecoder decoder) {
        MessageHeaderDecoder header = wrapHeader(buffer);
        decoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version());
    }

    public static void decode(DirectBuffer buffer, AuthDecoder decoder) {
        MessageHeaderDecoder header = wrapHeader(buffer);
        decoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version());
    }

    public static void decode(DirectBuffer buffer, OrderCancelDecoder decoder) {
        MessageHeaderDecoder header = wrapHeader(buffer);
        decoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version());
    }

    public static void decode(DirectBuffer buffer, DepositDecoder decoder) {
        MessageHeaderDecoder header = wrapHeader(buffer);
        decoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version());
    }

    public static void decode(DirectBuffer buffer, ExecutionReportDecoder decoder) {
        MessageHeaderDecoder header = wrapHeader(buffer);
        decoder.wrap(buffer, DEFAULT_OFFSET + MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version());
    }

    private static MessageHeaderDecoder wrapHeader(DirectBuffer buffer) {
        MessageHeaderDecoder header = ThreadContext.get().getHeaderDecoder();
        header.wrap(buffer, DEFAULT_OFFSET);
        return header;
    }
}
