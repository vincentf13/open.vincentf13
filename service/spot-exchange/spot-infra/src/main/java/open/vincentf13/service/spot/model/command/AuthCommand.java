package open.vincentf13.service.spot.model.command;

import lombok.Data;
import lombok.EqualsAndHashCode;
import open.vincentf13.service.spot.sbe.AuthDecoder;
import open.vincentf13.service.spot.sbe.AuthEncoder;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static open.vincentf13.service.spot.infra.Constants.MsgType;

/** 統一格式認證指令 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthCommand extends AbstractSbeModel {
    private final AuthEncoder encoder = new AuthEncoder();
    private final AuthDecoder decoder = new AuthDecoder();

    @Override protected void wrapDecoder(DirectBuffer buffer, int offset, int blockLength, int version) { decoder.wrap(buffer, offset, blockLength, version); }

    public AuthCommand write(MutableDirectBuffer dstBuffer, int offset) {
        this.buffer.wrap(dstBuffer, offset, encodedLength());
        return this;
    }

    public void set(long seq, long timestamp, long userId) {
        fillCommonHeader(MsgType.AUTH, seq, AuthEncoder.TEMPLATE_ID, AuthEncoder.BLOCK_LENGTH, AuthEncoder.SCHEMA_ID, AuthEncoder.SCHEMA_VERSION);
        encoder.wrap(buffer, BODY_OFFSET).timestamp(timestamp).userId(userId);
        refreshDecoder();
    }

    @Override public int encodedLength() { return BODY_OFFSET + AuthEncoder.BLOCK_LENGTH; }
    public long getUserId() { return decoder.userId(); }
    public long getTimestamp() { return decoder.timestamp(); }
}
