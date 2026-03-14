package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.AuthDecoder;
import open.vincentf13.service.spot.sbe.AuthEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 統一格式認證回報 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthReport extends AbstractSbeModel {
    private final AuthEncoder encoder = new AuthEncoder();
    private final AuthDecoder decoder = new AuthDecoder();

    @Override protected void decoderReWrap(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public AuthReport wrapWriteBuffer(MutableDirectBuffer dstBuffer, int offset) {
        this.unsafeBuffer.wrap(dstBuffer, offset, totalByteLength());
        return this;
    }

    public void set(long seq, long timestamp, long userId) {
        fillHeader(MsgType.AUTH_REPORT, seq, AuthEncoder.TEMPLATE_ID, AuthEncoder.BLOCK_LENGTH, AuthEncoder.SCHEMA_ID, AuthEncoder.SCHEMA_VERSION);
        encoder.wrap(unsafeBuffer, BODY_OFFSET).timestamp(timestamp).userId(userId);
        refreshDecoder();
    }

    @Override public int totalByteLength() { return BODY_OFFSET + AuthEncoder.BLOCK_LENGTH; }
    public long getUserId() { return decoder.userId(); }
    public long getTimestamp() { return decoder.timestamp(); }
}
